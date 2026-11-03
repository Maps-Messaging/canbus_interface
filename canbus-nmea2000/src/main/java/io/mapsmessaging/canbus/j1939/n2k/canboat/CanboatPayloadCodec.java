/*
 *   Copyright [ 2024 -  2026 ] MapsMessaging B.V.
 *
 *   Licensed under the Apache License, Version 2.0 with the Commons Clause
 *   (the "License"); you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at:
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *       https://commonsclause.com/
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package io.mapsmessaging.canbus.j1939.n2k.canboat;

import static io.mapsmessaging.canbus.j1939.n2k.canboat.CanboatValueCodec.bad;
import static io.mapsmessaging.canbus.j1939.n2k.canboat.CanboatValueCodec.proprietary;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.List;

/** Traverses fixed and repeating field sets using shared payload state. */
final class CanboatPayloadCodec {
  private static final String REPEATING_FIELD_SET = "RepeatingFieldSet";
  private static final String CONDITION = "Condition";

  private final CanboatFieldCodec fieldsCodec;

  CanboatPayloadCodec(CanboatDatabase database) {
    fieldsCodec = new CanboatFieldCodec(database);
  }

  void walk(CanboatMessage message, int from, int to, JsonObject packet, CanboatCodecState state, boolean encode, boolean repeated) {
    List<CanboatField> fields = message.fields();
    int index = from;
    while (index < to) {
      CanboatField field = fields.get(index);
      int group = repeated ? 0 : repeatingGroup(message, field);
      if (group > 0) {
        index += repeatingFields(message, index, group, packet, state, encode);
      } else if (!conditionApplies(message, field, state)) {
        index++;
      } else {
        if (!encode && state.bits.remaining() == 0 && !repeated) {
          return;
        }
        fieldsCodec.value(message, field, packet, state, encode, repeated);
        index++;
      }
    }
  }

  private static int repeatingGroup(CanboatMessage message, CanboatField field) {
    for (int group = 1; group <= 2; group++) {
      if (message.number(REPEATING_FIELD_SET + group + "StartField", -1) == field.order()) {
        return group;
      }
    }
    return 0;
  }

  private static boolean conditionApplies(CanboatMessage message, CanboatField field, CanboatCodecState state) {
    String condition = field.text(CONDITION);
    if ("PGNIsProprietary".equals(condition)) {
      return state.referencePgn != null && proprietary(state.referencePgn);
    }
    if (condition != null) {
      throw bad(message, "unsupported field condition " + condition);
    }
    return true;
  }

  private int repeatingFields(CanboatMessage message, int index, int group, JsonObject packet, CanboatCodecState state, boolean encode) {
    int count = message.number(REPEATING_FIELD_SET + group + "Size", 0);
    if (count < 1 || index + count > message.fields().size()) {
      throw bad(message, "invalid repeating-field descriptor");
    }
    int counter = message.number(REPEATING_FIELD_SET + group + "CountField", 0);
    String key = group == 1 ? "list" : "list2";
    JsonArray rows = new JsonArray();
    if (encode && packet.has(key)) {
      rows = packet.getAsJsonArray(key);
    }
    int repetitions = repetitionCount(message, state, rows, counter, encode);
    transferRows(message, index, count, rows, state, encode, repetitions);
    if (!encode && repetitions < 0 && state.bits.remaining() > 0 && !state.bits.paddingOnly()) {
      throw bad(message, "truncated repeating-field record");
    }
    if (!encode) {
      packet.add(key, rows);
    }
    return count;
  }

  private void transferRows(CanboatMessage message, int index, int count, JsonArray rows, CanboatCodecState state, boolean encode, int repetitions) {
    int minimumBits = repetitions < 0 ? minimumRepeatBits(message.fields(), index, count) : 0;
    for (int row = 0; repetitions < 0 ? state.bits.remaining() >= minimumBits : row < repetitions; row++) {
      if (row >= 65535) {
        throw bad(message, "too many repeated records");
      }
      JsonObject entry = encode ? rows.get(row).getAsJsonObject() : new JsonObject();
      transferRow(message, index, count, entry, state, encode);
      if (!encode) {
        rows.add(entry);
      }
    }
  }

  private void transferRow(CanboatMessage message, int index, int count, JsonObject entry, CanboatCodecState state, boolean encode) {
    int before = state.bits.position;
    walk(message, index, index + count, entry, state.child(), encode, true);
    if (state.bits.position <= before) {
      throw bad(message, "repeated record made no progress");
    }
  }

  private static int repetitionCount(CanboatMessage message, CanboatCodecState state, JsonArray rows, int counter, boolean encode) {
    if (encode) {
      return rows.size();
    }
    if (counter <= 0) {
      return -1;
    }
    BigInteger raw = state.raw.get(counter);
    if (raw == null || raw.signum() < 0 || raw.compareTo(BigInteger.valueOf(65535)) > 0) {
      throw bad(message, "invalid repeating-field count");
    }
    return raw.intValue();
  }

  private static int minimumRepeatBits(List<CanboatField> fields, int start, int count) {
    int bits = 0;
    for (int index = start; index < start + count; index++) {
      bits += Math.max(0, fields.get(index).number("BitLength", 0));
    }
    return Math.max(8, bits);
  }
}
