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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataTypeCodecBoundaryTest {

  @Test
  void signedThreeByteValuesRoundTripAtBothLimits() {
    for (int value : new int[]{-8388608, -1, 0, 8388607}) {
      assertEquals(value, DataTypeCodec.decode("VARIABLE3", DataTypeCodec.encode("VARIABLE3", value)));
    }
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("VARIABLE3", -8388609));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("VARIABLE3", 8388608));
  }

  @Test
  void unsignedThreeByteValuesRoundTripAtBothLimits() {
    for (int value : new int[]{0, 1, 0xFFFFFF}) {
      assertEquals(value, DataTypeCodec.decode("UVARIABLE3", DataTypeCodec.encode("UVARIABLE3", value)));
    }
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("UVARIABLE3", -1));
    assertThrows(IllegalArgumentException.class, () -> DataTypeCodec.encode("UVARIABLE3", 0x1000000));
  }

  @Test
  void unknownTypePreservesRawDecodeAndCopiesRawEncode() {
    byte[] payload = new byte[]{1, 2, 3, 4};
    assertSame(payload, DataTypeCodec.decode("VENDOR", payload));
    byte[] encoded = DataTypeCodec.encode("VENDOR", payload);
    assertArrayEquals(payload, encoded);
    payload[0] = 9;
    assertEquals(1, encoded[0]);
    assertNull(DataTypeCodec.decode("NODATA", encoded));
  }
}
