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

import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.j1939.n2k.codec.FieldValueSource;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Message-payload codec for generated CANboat XML. This class has no transport state. */
public final class CanboatCodec {
  private static final String PACKET = "packet";

  private final CanboatDatabase database;
  private final CanboatMessageSelector selector;
  private final CanboatPayloadCodec payloadCodec;

  public CanboatCodec(CanboatDatabase database) {
    this.database = Objects.requireNonNull(database);
    selector = new CanboatMessageSelector(database);
    payloadCodec = new CanboatPayloadCodec(database);
  }

  public JsonObject decode(int pgn, byte[] payload) {
    Objects.requireNonNull(payload, "payload");
    CanboatMessage message = selector.select(pgn, payload, null);
    if (message == null) {
      return null;
    }
    if (payload.length < message.minimum()) {
      throw bad(message, "payload shorter than minimum " + message.minimum());
    }
    JsonObject packet = new JsonObject();
    if (message.fallback()) {
      packet.addProperty("data", Base64.getEncoder().encodeToString(payload));
    } else {
      payloadCodec.walk(message, 0, message.fields().size(), packet, new CanboatCodecState(new CanboatBits(payload), packet), false, false);
    }
    JsonObject envelope = new JsonObject();
    envelope.add(PACKET, packet);
    envelope.addProperty("name", message.id());
    return envelope;
  }

  public byte[] encode(int pgn, JsonObject envelope) {
    Objects.requireNonNull(envelope, "envelope");
    if (!envelope.has(PACKET) || !envelope.get(PACKET).isJsonObject()) {
      throw new IllegalArgumentException("Missing 'packet' object");
    }
    JsonObject packet = envelope.getAsJsonObject(PACKET).deepCopy();
    String name = envelope.has("name") ? envelope.get("name").getAsString() : null;
    CanboatMessage message = selector.select(pgn, null, packet, name);
    if (message == null) {
      return new byte[0];
    }
    if (message.fallback()) {
      return packet.has("data") ? Base64.getDecoder().decode(packet.get("data").getAsString()) : new byte[0];
    }
    populateRepeatCounters(message, packet);
    CanboatCodecState state = new CanboatCodecState(new CanboatBits(), packet);
    payloadCodec.walk(message, 0, message.fields().size(), packet, state, true, false);
    int actual = (state.bits.position + 7) / 8;
    int size = message.length() >= 0 ? message.length() : Math.max(actual, message.minimum());
    if (actual > size) {
      throw bad(message, "encoded payload exceeds declared length");
    }
    return state.bits.finish(size);
  }

  public byte[] encode(int pgn, FieldValueSource source) {
    Objects.requireNonNull(source, "source");
    List<CanboatMessage> candidates = database.candidates(pgn);
    if (candidates.isEmpty()) {
      return new byte[0];
    }
    JsonObject packet = new JsonObject();
    for (CanboatMessage message : candidates) {
      for (CanboatField field : message.fields()) {
        addSourceValue(packet, field, source);
      }
    }
    JsonObject envelope = new JsonObject();
    envelope.add(PACKET, packet);
    return encode(pgn, envelope);
  }

  private static void populateRepeatCounters(CanboatMessage message, JsonObject packet) {
    for (int group = 1; group <= 2; group++) {
      int counter = message.number("RepeatingFieldSet" + group + "CountField", 0);
      CanboatField field = counter > 0 ? message.field(counter) : null;
      if (field != null) {
        String key = group == 1 ? "list" : "list2";
        packet.addProperty(field.id(), packet.has(key) ? packet.getAsJsonArray(key).size() : 0);
      }
    }
  }

  private static void addSourceValue(JsonObject packet, CanboatField field, FieldValueSource source) {
    if (!source.has(field.id())) {
      return;
    }
    if (field.type().startsWith("STRING") || field.type().equals("BINARY") || field.type().equals("DECIMAL")) {
      String text = source.getString(field.id());
      if (text != null) {
        packet.addProperty(field.id(), text);
      }
      return;
    }
    Double number = source.getDouble(field.id());
    Long integer = source.getLong(field.id());
    if (number != null) {
      packet.addProperty(field.id(), number);
    } else if (integer != null) {
      packet.addProperty(field.id(), integer);
    }
  }
}
