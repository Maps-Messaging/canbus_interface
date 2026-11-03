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

import static io.mapsmessaging.canbus.j1939.n2k.canboat.CanboatValueCodec.numericRaw;

import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Selects named/discriminated layouts while retaining protocol fallback rules. */
final class CanboatMessageSelector {
  private final CanboatDatabase database;

  CanboatMessageSelector(CanboatDatabase database) {
    this.database = database;
  }

  CanboatMessage select(int pgn, byte[] wire, JsonObject packet) {
    return select(pgn, wire, packet, null);
  }

  CanboatMessage select(int pgn, byte[] wire, JsonObject packet, String name) {
    List<CanboatMessage> candidates = database.candidates(pgn);
    if (candidates.isEmpty()) {
      return null;
    }
    List<CanboatMessage> matching = new ArrayList<>();
    for (CanboatMessage message : candidates) {
      if ((name == null || name.equals(message.id())) && !message.fallback()
          && matches(message, wire, packet, wire == null && name == null && candidates.stream().filter(m -> !m.fallback()).count() > 1)) {
        matching.add(message);
      }
    }
    if (!matching.isEmpty()) {
      if (wire == null && matching.size() > 1) {
        throw new IllegalArgumentException("Ambiguous CANboat variant for PGN " + pgn + "; supply name or discriminator fields");
      }
      return matching.get(0);
    }
    return selectFallback(pgn, wire, name, candidates);
  }

  private CanboatMessage selectFallback(int pgn, byte[] wire, String name, List<CanboatMessage> candidates) {
    CanboatMessage range = database.fallback(pgn);
    if (range != null && ((wire != null && name == null) || range.id().equals(name))) {
      return range;
    }
    if (name != null) {
      for (CanboatMessage message : candidates) {
        if (message.fallback() && name.equals(message.id())) {
          return message;
        }
      }
    }
    if (name != null) {
      throw new IllegalArgumentException("Unknown or conflicting CANboat variant '" + name + "' for PGN " + pgn);
    }
    for (CanboatMessage message : candidates) {
      if (message.fallback()) {
        return message;
      }
    }
    throw new IllegalArgumentException("No matching CANboat variant for PGN " + pgn);
  }

  private boolean matches(CanboatMessage message, byte[] wire, JsonObject packet, boolean requireMatchValues) {
    for (CanboatField field : message.fields()) {
      BigInteger expected = field.integer("Match");
      if (expected != null && !matchesField(field, expected, wire, packet, requireMatchValues)) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesField(CanboatField field, BigInteger expected, byte[] wire, JsonObject packet, boolean requireMatchValues) {
    if (wire != null) {
      int start = field.number("BitOffset", -1);
      int length = field.number("BitLength", 0);
      if (start < 0 || length < 1 || start + length > wire.length * 8) {
        return false;
      }
      CanboatBits bits = new CanboatBits(wire);
      bits.position = start;
      return expected.equals(bits.read(length));
    }
    if (packet != null && packet.has(field.id()) && !packet.get(field.id()).isJsonNull()) {
      return expected.equals(numericRaw(field, packet.get(field.id())));
    }
    return !requireMatchValues;
  }
}
