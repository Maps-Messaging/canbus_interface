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

import io.mapsmessaging.canbus.device.frames.CanFrame;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlcanCanFrameStreamCodecTest {

  @Test
  void shouldReadExtendedFrame() throws IOException {
    SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        "T18F1120A8C0F4B2161BD8330E\r".getBytes()
    );

    CanFrame canFrame = codec.read(inputStream);

    assertEquals(0x18F1120A, canFrame.canIdentifier());
    assertEquals(true, canFrame.extendedFrame());
    assertEquals(8, canFrame.dataLengthCode());
    assertArrayEquals(
        new byte[]{
            (byte) 0xC0,
            (byte) 0xF4,
            (byte) 0xB2,
            0x16,
            0x1B,
            (byte) 0xD8,
            0x33,
            0x0E
        },
        canFrame.data()
    );
  }

  @Test
  void shouldReadStandardFrame() throws IOException {
    SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
    ByteArrayInputStream inputStream = new ByteArrayInputStream("t1234AABBCCDD\r".getBytes());

    CanFrame canFrame = codec.read(inputStream);

    assertEquals(0x123, canFrame.canIdentifier());
    assertEquals(false, canFrame.extendedFrame());
    assertEquals(4, canFrame.dataLengthCode());
    assertArrayEquals(
        new byte[]{
            (byte) 0xAA,
            (byte) 0xBB,
            (byte) 0xCC,
            (byte) 0xDD
        },
        canFrame.data()
    );
  }

  @Test
  void shouldWriteExtendedFrame() throws IOException {
    SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CanFrame canFrame = new CanFrame(
        0x18F1120A,
        true,
        8,
        new byte[]{
            (byte) 0xC0,
            (byte) 0xF4,
            (byte) 0xB2,
            0x16,
            0x1B,
            (byte) 0xD8,
            0x33,
            0x0E
        }
    );

    codec.write(outputStream, canFrame);

    assertEquals("T18F1120A8C0F4B2161BD8330E\r", outputStream.toString());
  }

  @Test
  void shouldWriteStandardFrame() throws IOException {
    SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    CanFrame canFrame = new CanFrame(
        0x123,
        false,
        4,
        new byte[]{
            (byte) 0xAA,
            (byte) 0xBB,
            (byte) 0xCC,
            (byte) 0xDD
        }
    );

    codec.write(outputStream, canFrame);

    assertEquals("t1234AABBCCDD\r", outputStream.toString());
  }

  @Test
  void shouldRoundTripExtendedFrame() throws IOException {
    SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
    CanFrame expected = new CanFrame(
        0x18F1120A,
        true,
        8,
        new byte[]{
            (byte) 0xC0,
            (byte) 0xF4,
            (byte) 0xB2,
            0x16,
            0x1B,
            (byte) 0xD8,
            0x33,
            0x0E
        }
    );

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    codec.write(outputStream, expected);

    ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
    CanFrame actual = codec.read(inputStream);

    assertEquals(expected.canIdentifier(), actual.canIdentifier());
    assertEquals(expected.extendedFrame(), actual.extendedFrame());
    assertEquals(expected.dataLengthCode(), actual.dataLengthCode());
    assertArrayEquals(expected.data(), actual.data());
  }

  @Test
  void shouldReadNullAtEndOfStream() throws IOException {
    SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0]);

    CanFrame canFrame = codec.read(inputStream);

    assertNull(canFrame);
  }

  @Test
  void shouldSkipBlankLines() throws IOException {
    SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        "\r\n\r\nT18F1120A8C0F4B2161BD8330E\r".getBytes()
    );

    CanFrame canFrame = codec.read(inputStream);

    assertEquals(0x18F1120A, canFrame.canIdentifier());
    assertEquals(true, canFrame.extendedFrame());
    assertEquals(8, canFrame.dataLengthCode());
  }

  @Test
  void shouldRejectUnsupportedFrameType() {
    SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
    ByteArrayInputStream inputStream = new ByteArrayInputStream("x1234AABB\r".getBytes());

    assertThrows(IOException.class, () -> codec.read(inputStream));
  }

  @Test
  void shouldRejectInvalidFrameLength() {
    SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        "T18F1120A8C0F4B2161BD833\r".getBytes()
    );

    assertThrows(IOException.class, () -> codec.read(inputStream));
  }

  @Test
  void shouldWriteOpenCommand() throws IOException {
    SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    codec.open(outputStream);

    assertEquals("O\r", outputStream.toString());
  }

  @Test
  void shouldWriteCloseCommand() throws IOException {
    SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    codec.close(outputStream);

    assertEquals("C\r", outputStream.toString());
  }

  @Test
  void shouldWriteBitRateCommand() throws IOException {
    SlcanCanFrameStreamCodec codec = new SlcanCanFrameStreamCodec();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    codec.setBitRate(outputStream, SlcanBitRate.CAN_500K);

    assertEquals("S6\r", outputStream.toString());
  }
}