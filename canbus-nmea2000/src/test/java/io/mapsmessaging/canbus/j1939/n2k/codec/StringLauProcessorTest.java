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
package io.mapsmessaging.canbus.j1939.n2k.codec;

import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StringLauProcessorTest {

  private final N2kCompiledField field = N2kCompiledField.builder().id("Text").build();
  private final StringLauProcessor processor = new StringLauProcessor();

  @Test
  void utf8PayloadIncludesByteLengthAndEncodingMarker() {
    JsonObject input = new JsonObject();
    input.addProperty("Text", "µ");
    byte[] payload = new byte[4];

    assertEquals(4, processor.computePayloadLength(field, input));
    assertEquals(4, processor.pack(field, payload, 0, input));
    assertArrayEquals(new byte[]{3, 1, (byte) 0xC2, (byte) 0xB5}, payload);

    JsonObject decoded = new JsonObject();
    assertEquals(4, processor.unpack(field, payload, 0, decoded));
    assertEquals("µ", decoded.get("Text").getAsString());
  }

  @Test
  void nonUtf8EncodingMarkerUsesLatin1() {
    JsonObject decoded = new JsonObject();

    int next = processor.unpack(field, new byte[]{3, 0, (byte) 0xE9, 'A'}, 0, decoded);

    assertEquals(4, next);
    assertEquals("éA", decoded.get("Text").getAsString());
  }

  @Test
  void truncatedPayloadDoesNotReadBeyondAvailableBytes() {
    JsonObject decoded = new JsonObject();

    int next = processor.unpack(field, new byte[]{6, 1, 'A', 'B'}, 0, decoded);

    assertEquals(4, next);
    assertEquals("AB", decoded.get("Text").getAsString());
  }

  @Test
  void absentEmptyAndZeroLengthStringsProduceNoValue() {
    JsonObject empty = new JsonObject();
    byte[] payload = new byte[5];
    assertEquals(0, processor.computePayloadLength(field, empty));
    assertEquals(2, processor.pack(field, payload, 2, empty));

    empty.addProperty("Text", "");
    assertEquals(0, processor.computePayloadLength(field, empty));
    assertEquals(2, processor.pack(field, payload, 2, empty));

    JsonObject decoded = new JsonObject();
    assertEquals(0, processor.unpack(field, new byte[0], 0, decoded));
    assertEquals(1, processor.unpack(field, new byte[]{0}, 0, decoded));
    assertEquals(1, processor.unpack(field, new byte[]{2}, 0, decoded));
    assertFalse(decoded.has("Text"));
  }

  @Test
  void fieldValueSourceUsesSameUtf8WireFormat() {
    FieldValueSource source = org.mockito.Mockito.mock(FieldValueSource.class);
    org.mockito.Mockito.when(source.getString("Text")).thenReturn("µ");
    byte[] payload = new byte[5];

    assertEquals(4, processor.computePayloadLength(field, source));
    assertEquals(5, processor.pack(field, payload, 1, source));
    assertArrayEquals(new byte[]{0, 3, 1, (byte) 0xC2, (byte) 0xB5}, payload);
  }

  @Test
  void fieldValueSourceMissingAndEmptyTextProduceNoPayload() {
    FieldValueSource source = org.mockito.Mockito.mock(FieldValueSource.class);
    byte[] payload = new byte[4];

    assertEquals(0, processor.computePayloadLength(field, source));
    assertEquals(2, processor.pack(field, payload, 2, source));

    org.mockito.Mockito.when(source.getString("Text")).thenReturn("");
    assertEquals(0, processor.computePayloadLength(field, source));
    assertEquals(2, processor.pack(field, payload, 2, source));
  }
}
