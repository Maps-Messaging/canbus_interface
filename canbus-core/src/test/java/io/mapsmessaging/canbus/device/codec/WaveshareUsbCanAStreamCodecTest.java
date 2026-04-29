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

package io.mapsmessaging.canbus.device.codec;

import io.mapsmessaging.canbus.device.codec.impl.WaveshareUsbCanAStreamCodec;
import io.mapsmessaging.canbus.device.frames.CanFrame;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveshareUsbCanAStreamCodecTest {

  private final WaveshareUsbCanAStreamCodec codec = new WaveshareUsbCanAStreamCodec();

  @Test
  void testReadStandardFrame() throws IOException {
    byte[] packet = new byte[]{
        (byte) 0xAA,
        (byte) 0xC3,
        (byte) 0x23,
        (byte) 0x01,
        (byte) 0x11,
        (byte) 0x22,
        (byte) 0x33,
        (byte) 0x55
    };

    CanFrame canFrame = codec.read(new ByteArrayInputStream(packet));

    assertEquals(0x123, canFrame.canIdentifier());
    assertFalse(canFrame.extendedFrame());
    assertEquals(3, canFrame.dataLengthCode());
    assertArrayEquals(
        new byte[]{
            (byte) 0x11,
            (byte) 0x22,
            (byte) 0x33
        },
        canFrame.data()
    );
  }

  @Test
  void testReadExtendedFrame() throws IOException {
    byte[] packet = new byte[]{
        (byte) 0xAA,
        (byte) 0xE2,
        (byte) 0x67,
        (byte) 0x45,
        (byte) 0x23,
        (byte) 0x01,
        (byte) 0x7A,
        (byte) 0x7B,
        (byte) 0x55
    };

    CanFrame canFrame = codec.read(new ByteArrayInputStream(packet));

    assertEquals(0x01234567, canFrame.canIdentifier());
    assertTrue(canFrame.extendedFrame());
    assertEquals(2, canFrame.dataLengthCode());
    assertArrayEquals(
        new byte[]{
            (byte) 0x7A,
            (byte) 0x7B
        },
        canFrame.data()
    );
  }

  @Test
  void testReadPayloadContainingEndByte() throws IOException {
    byte[] packet = new byte[]{
        (byte) 0xAA,
        (byte) 0xC4,
        (byte) 0x23,
        (byte) 0x01,
        (byte) 0x11,
        (byte) 0x55,
        (byte) 0x22,
        (byte) 0x33,
        (byte) 0x55
    };

    CanFrame canFrame = codec.read(new ByteArrayInputStream(packet));

    assertEquals(0x123, canFrame.canIdentifier());
    assertFalse(canFrame.extendedFrame());
    assertEquals(4, canFrame.dataLengthCode());
    assertArrayEquals(
        new byte[]{
            (byte) 0x11,
            (byte) 0x55,
            (byte) 0x22,
            (byte) 0x33
        },
        canFrame.data()
    );
  }

  @Test
  void testReadSkipsNoiseBeforeHeader() throws IOException {
    byte[] packet = new byte[]{
        (byte) 0x00,
        (byte) 0x12,
        (byte) 0x34,
        (byte) 0xAA,
        (byte) 0xC1,
        (byte) 0x23,
        (byte) 0x01,
        (byte) 0x42,
        (byte) 0x55
    };

    CanFrame canFrame = codec.read(new ByteArrayInputStream(packet));

    assertEquals(0x123, canFrame.canIdentifier());
    assertFalse(canFrame.extendedFrame());
    assertEquals(1, canFrame.dataLengthCode());
    assertArrayEquals(
        new byte[]{
            (byte) 0x42
        },
        canFrame.data()
    );
  }

  @Test
  void testWriteStandardFrame() throws IOException {
    CanFrame canFrame = new CanFrame(
        0x123,
        false,
        3,
        new byte[]{
            (byte) 0x11,
            (byte) 0x22,
            (byte) 0x33
        }
    );

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    codec.write(outputStream, canFrame);

    assertArrayEquals(
        new byte[]{
            (byte) 0xAA,
            (byte) 0xC3,
            (byte) 0x23,
            (byte) 0x01,
            (byte) 0x11,
            (byte) 0x22,
            (byte) 0x33,
            (byte) 0x55
        },
        outputStream.toByteArray()
    );
  }

  @Test
  void testWriteExtendedFrame() throws IOException {
    CanFrame canFrame = new CanFrame(
        0x01234567,
        true,
        2,
        new byte[]{
            (byte) 0x7A,
            (byte) 0x7B
        }
    );

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    codec.write(outputStream, canFrame);

    assertArrayEquals(
        new byte[]{
            (byte) 0xAA,
            (byte) 0xE2,
            (byte) 0x67,
            (byte) 0x45,
            (byte) 0x23,
            (byte) 0x01,
            (byte) 0x7A,
            (byte) 0x7B,
            (byte) 0x55
        },
        outputStream.toByteArray()
    );
  }

  @Test
  void testReadRejectsInvalidEndMarker() {
    byte[] packet = new byte[]{
        (byte) 0xAA,
        (byte) 0xC1,
        (byte) 0x23,
        (byte) 0x01,
        (byte) 0x42,
        (byte) 0x99
    };

    assertThrows(
        IOException.class,
        () -> codec.read(new ByteArrayInputStream(packet))
    );
  }

  @Test
  void testReadRejectsInvalidDlc() {
    byte[] packet = new byte[]{
        (byte) 0xAA,
        (byte) 0xC9,
        (byte) 0x23,
        (byte) 0x01,
        (byte) 0x55
    };

    assertThrows(
        IOException.class,
        () -> codec.read(new ByteArrayInputStream(packet))
    );
  }

  @Test
  void testReadRejectsRemoteFrame() {
    byte[] packet = new byte[]{
        (byte) 0xAA,
        (byte) 0xD0,
        (byte) 0x23,
        (byte) 0x01,
        (byte) 0x55
    };

    assertThrows(
        IOException.class,
        () -> codec.read(new ByteArrayInputStream(packet))
    );
  }

  @Test
  void testWriteUsesOnlyDlcBytesFromPayload() throws IOException {
    CanFrame canFrame = new CanFrame(
        0x123,
        false,
        2,
        new byte[]{
            (byte) 0x11,
            (byte) 0x22,
            (byte) 0x33,
            (byte) 0x44
        }
    );

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    codec.write(outputStream, canFrame);

    assertArrayEquals(
        new byte[]{
            (byte) 0xAA,
            (byte) 0xC2,
            (byte) 0x23,
            (byte) 0x01,
            (byte) 0x11,
            (byte) 0x22,
            (byte) 0x55
        },
        outputStream.toByteArray()
    );
  }
}