/*
 *   Copyright [ 2024 - 2026 ] MapsMessaging B.V.
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
 */
package io.mapsmessaging.canbus.j1939.n2k.canboat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanboatTextBoundaryTest {
  @Test
  void decodesLauNullPaddingBeforeFinalLineTerminators() {
    Map<String, String> cases = Map.ofEntries(Map.entry("A\u0000", "A"), Map.entry("A\u0000\n", "A"), Map.entry("A\u0000\r\n", "A"), Map.entry("A\u0000\r", "A"),
        Map.entry("A\u0000\u0085", "A\u0085"), Map.entry("A\u0000\u2028", "A"), Map.entry("A\u0000\u2029", "A"), Map.entry("A\u0000\n\u0000", "A\u0000"),
        Map.entry("A\u0000\n\n", "A\u0000"), Map.entry("A\u0000 B", "A\u0000 B"));
    CanboatField field = new CanboatField(Map.of("FieldType", "STRING_LAU"));
    for (Map.Entry<String, String> entry : cases.entrySet()) {
      byte[] text = entry.getKey().getBytes(StandardCharsets.UTF_16LE);
      byte[] payload = new byte[text.length + 2];
      payload[0] = (byte) payload.length;
      payload[1] = 0; // LAU encoding zero denotes UTF-16LE.
      System.arraycopy(text, 0, payload, 2, text.length);
      assertEquals(entry.getValue(), CanboatValueCodec.string(field, null, new CanboatBits(payload), false).getAsString());
    }
  }
}
