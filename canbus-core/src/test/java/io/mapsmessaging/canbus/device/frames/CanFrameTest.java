/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      https://commonsclause.com/
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.mapsmessaging.canbus.device.frames;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CanFrameTest {

  @Test
  void roundTrip_standardFrame_dlc8_preservesFieldsAndData() {
    int canIdentifier = 0x09F8027F;
    boolean extendedFrame = false;
    int dataLengthCode = 8;
    byte[] data = new byte[]{0x00, (byte) 0xFC, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF};

    CanFrame original = new CanFrame(canIdentifier, extendedFrame, dataLengthCode, data);
    byte[] raw = original.getRawData();
    CanFrame decoded = CanFrame.fromBytes(raw);

    Assertions.assertEquals(canIdentifier, decoded.canIdentifier());
    Assertions.assertEquals(extendedFrame, decoded.extendedFrame());
    Assertions.assertEquals(dataLengthCode, decoded.dataLengthCode());
    Assertions.assertArrayEquals(data, decoded.data());
  }

  @Test
  void roundTrip_extendedFrame_dlc0_preservesFieldsAndEmptyData() {
    int canIdentifier = 0x1ABCDEFF;
    boolean extendedFrame = true;
    int dataLengthCode = 0;

    CanFrame original = new CanFrame(canIdentifier, extendedFrame, dataLengthCode, new byte[0]);
    byte[] raw = original.getRawData();
    CanFrame decoded = CanFrame.fromBytes(raw);

    Assertions.assertEquals(canIdentifier, decoded.canIdentifier());
    Assertions.assertEquals(extendedFrame, decoded.extendedFrame());
    Assertions.assertEquals(dataLengthCode, decoded.dataLengthCode());
    Assertions.assertArrayEquals(new byte[0], decoded.data());
  }

  @Test
  void getRawData_encodesCanIdentifierBigEndian() {
    int canIdentifier = 0x01020304;

    CanFrame frame = new CanFrame(canIdentifier, false, 0, new byte[0]);
    byte[] raw = frame.getRawData();

    Assertions.assertEquals((byte) 0x01, raw[0]);
    Assertions.assertEquals((byte) 0x02, raw[1]);
    Assertions.assertEquals((byte) 0x03, raw[2]);
    Assertions.assertEquals((byte) 0x04, raw[3]);
  }

  @Test
  void getRawData_encodesFlags_extendedAndDlc() {
    int canIdentifier = 0x11223344;
    boolean extendedFrame = true;
    int dataLengthCode = 8;

    CanFrame frame = new CanFrame(canIdentifier, extendedFrame, dataLengthCode, new byte[8]);
    byte[] raw = frame.getRawData();

    int flags = raw[4] & 0xFF;
    Assertions.assertTrue((flags & 0x01) != 0);

    int decodedDlc = (flags >>> 1) & 0x0F;
    Assertions.assertEquals(dataLengthCode, decodedDlc);
  }

  @Test
  void getRawData_truncatesPayloadTo8Bytes() {
    int canIdentifier = 0x09F8017F;
    boolean extendedFrame = false;
    int dataLengthCode = 8;

    byte[] data = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    CanFrame frame = new CanFrame(canIdentifier, extendedFrame, dataLengthCode, data);

    byte[] raw = frame.getRawData();

    byte[] payload = new byte[8];
    System.arraycopy(raw, 5, payload, 0, 8);

    Assertions.assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5, 6, 7}, payload);
  }

  @Test
  void fromBytes_rejectsNull() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> CanFrame.fromBytes(null));
  }

  @Test
  void fromBytes_rejectsTooShort() {
    byte[] raw = new byte[12];
    Assertions.assertThrows(IllegalArgumentException.class, () -> CanFrame.fromBytes(raw));
  }

  @Test
  void constructor_defensivelyCopiesData() {
    byte[] data = new byte[]{1, 2, 3, 4};
    CanFrame frame = new CanFrame(0x12345678, false, 4, data);

    data[0] = 99;

    byte[] retrieved = frame.data();
    Assertions.assertArrayEquals(new byte[]{1, 2, 3, 4}, retrieved);
  }

  @Test
  void getData_returnsDefensiveCopy() {
    byte[] data = new byte[]{10, 20, 30, 40};
    CanFrame frame = new CanFrame(0x12345678, false, 4, data);

    byte[] retrieved = frame.data();
    retrieved[0] = 99;

    Assertions.assertArrayEquals(new byte[]{10, 20, 30, 40}, frame.data());
  }
}
