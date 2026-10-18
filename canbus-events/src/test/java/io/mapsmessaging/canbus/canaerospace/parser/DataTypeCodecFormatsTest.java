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
package io.mapsmessaging.canbus.canaerospace.parser;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataTypeCodecFormatsTest {

  @Test
  void signedAndUnsignedScalarsRoundTripAtWireBoundaries() {
    assertEquals(-42.5f, DataTypeCodec.decode("FLOAT", DataTypeCodec.encode("FLOAT", -42.5f)));
    assertEquals(Integer.MIN_VALUE, DataTypeCodec.decode("LONG", DataTypeCodec.encode("LONG", Integer.MIN_VALUE)));
    assertEquals(0xFFFFFFFFL, DataTypeCodec.decode("ULONG", DataTypeCodec.encode("ULONG", 0xFFFFFFFFL)));
    assertEquals(-32768, DataTypeCodec.decode("SHORT", DataTypeCodec.encode("SHORT", -32768)));
    assertEquals(65535, DataTypeCodec.decode("USHORT", DataTypeCodec.encode("USHORT", 65535)));
    assertEquals(-128, DataTypeCodec.decode("CHAR", DataTypeCodec.encode("CHAR", -128)));
    assertEquals(255, DataTypeCodec.decode("UCHAR", DataTypeCodec.encode("UCHAR", 255)));
    assertEquals(255, DataTypeCodec.decode("ACHAR", DataTypeCodec.encode("ACHAR", 255)));
    assertEquals(65535, DataTypeCodec.decode("BSHORT", DataTypeCodec.encode("BSHORT", 65535)));
    for (String type : List.of("BLONG", "MEMID", "CHKSUM")) {
      assertEquals(0xFFFFFFFFL, DataTypeCodec.decode(type, DataTypeCodec.encode(type, 0xFFFFFFFFL)), type);
    }
  }

  @Test
  void multivalueTypesKeepElementSignAndByteOrder() {
    assertArrayEquals(new int[]{-128, 127},
        (int[]) DataTypeCodec.decode("CHAR2", DataTypeCodec.encode("CHAR2", new int[]{-128, 127})));
    assertArrayEquals(new int[]{255, 0},
        (int[]) DataTypeCodec.decode("UCHAR2", DataTypeCodec.encode("UCHAR2", new Integer[]{255, 0})));
    assertArrayEquals(new int[]{255, 0},
        (int[]) DataTypeCodec.decode("ACHAR2", DataTypeCodec.encode("ACHAR2", List.of(255, 0))));
    assertArrayEquals(new int[]{-128, -1, 0, 127},
        (int[]) DataTypeCodec.decode("CHAR4", DataTypeCodec.encode("CHAR4", new int[]{-128, -1, 0, 127})));
    assertArrayEquals(new int[]{0, 1, 254, 255},
        (int[]) DataTypeCodec.decode("UCHAR4", DataTypeCodec.encode("UCHAR4", new int[]{0, 1, 254, 255})));
    assertArrayEquals(new int[]{-32768, 32767},
        (int[]) DataTypeCodec.decode("SHORT2", DataTypeCodec.encode("SHORT2", new int[]{-32768, 32767})));
    assertArrayEquals(new int[]{65535, 1},
        (int[]) DataTypeCodec.decode("USHORT2", DataTypeCodec.encode("USHORT2", new int[]{65535, 1})));
  }

  @Test
  void numericFieldsUseDocumentedWirePositions() {
    assertArrayEquals(new byte[]{0, 0, (byte) 0xFF, (byte) 0xFE}, DataTypeCodec.encode("SHORT", -2));
    assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFE, 0, 0}, DataTypeCodec.encode("BSHORT", 65534));
    assertArrayEquals(new byte[]{0, 0, 0, 0}, DataTypeCodec.encode("NODATA", null));
    assertNull(DataTypeCodec.decode("NODATA", new byte[4]));
  }

  @Test
  void textFieldTruncatesOrPadsToFourBytes() {
    assertArrayEquals(new byte[]{'A', 'B', 0, 0}, DataTypeCodec.encode("ACHAR4", "AB"));
    assertArrayEquals(new byte[]{'A', 'B', 'C', 'D'}, DataTypeCodec.encode("ACHAR4", "ABCDE"));
    assertEquals("ABCD", DataTypeCodec.decode("ACHAR4", new byte[]{'A', 'B', 'C', 'D'}));
    assertArrayEquals(new byte[]{'A', 'B', 'C', 'D'},
        DataTypeCodec.encode("ACHAR4", new int[]{65, 66, 67, 68}));
    assertArrayEquals(new byte[]{'A', 'B', 'C', 'D'},
        DataTypeCodec.encode("ACHAR4", new byte[]{'A', 'B', 'C', 'D'}));
  }

  @Test
  void rejectsOutOfRangeValuesWithoutTruncatingPayloads() {
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("ULONG", -1L));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("ULONG", 0x100000000L));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("SHORT", 32768));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("USHORT", -1));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("CHAR", 128));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("UCHAR", 256));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("BSHORT", -1));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("BLONG", 0x100000000L));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("CHAR2", new int[]{-129, 0}));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("UCHAR4", new int[]{0, 0, 0, 256}));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("SHORT2", new int[]{32768, 0}));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("USHORT2", new int[]{0, -1}));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("ACHAR4", new int[]{0, 0, 0, 256}));
  }

  @Test
  void rejectsMalformedArraysAndUnsupportedValues() {
    assertNull(DataTypeCodec.decode(null, null));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.decode("SHORT", new byte[3]));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode(null, 1));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("CHAR2", new int[]{1}));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("CHAR2", List.of("bad", 2)));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("UCHAR4", new Integer[]{1}));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("ACHAR4", new byte[3]));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("FLOAT", new Object()));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("VENDOR", new byte[3]));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("VENDOR", 1));
  }
}
