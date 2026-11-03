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

import java.math.BigInteger;
import java.util.Arrays;

/** Little-endian, bit-addressed payload buffer with bounded writes. */
final class CanboatBits {
  byte[] data;
  final boolean input;
  int position;

  CanboatBits(byte[] bytes) {
    data = bytes;
    input = true;
  }

  CanboatBits() {
    data = new byte[256];
    Arrays.fill(data, (byte) 255);
    input = false;
  }

  int remaining() {
    return data.length * 8 - position;
  }

  int bytesLength() {
    return data.length;
  }

  boolean paddingOnly() {
    for (int bit = position; bit < data.length * 8; bit++) {
      if (((data[bit / 8] & 0xff) & (1 << (bit % 8))) == 0) {
        return false;
      }
    }
    return true;
  }

  BigInteger read(int length) {
    byte[] bytes = readBytes(length);
    byte[] reversed = new byte[bytes.length + 1];
    for (int index = 0; index < bytes.length; index++) {
      reversed[bytes.length - index] = bytes[index];
    }
    return new BigInteger(reversed);
  }

  void write(BigInteger value, int length) {
    if (length < 0 || position + (long) length > 8_000_000) {
      throw new IllegalArgumentException("CANboat payload is too large");
    }
    int required = (position + length + 7) / 8;
    if (required > data.length) {
      int previous = data.length;
      data = Arrays.copyOf(data, Math.max(required, previous * 2));
      Arrays.fill(data, previous, data.length, (byte) 255);
    }
    for (int index = 0; index < length; index++) {
      int byteIndex = (position + index) / 8;
      int bit = 1 << ((position + index) % 8);
      if (value.testBit(index)) {
        data[byteIndex] |= (byte) bit;
      } else {
        data[byteIndex] &= (byte) ~bit;
      }
    }
    position += length;
  }

  byte[] readBytes(int length) {
    if (length < 0 || length > remaining()) {
      throw new IllegalArgumentException("Truncated CANboat field at bit " + position + ", needs " + length);
    }
    byte[] bytes = new byte[(length + 7) / 8];
    for (int index = 0; index < length; index++) {
      if (((data[(position + index) / 8] & 0xff) & (1 << ((position + index) % 8))) != 0) {
        bytes[index / 8] |= (byte) (1 << (index % 8));
      }
    }
    position += length;
    return bytes;
  }

  void writeBytes(byte[] bytes, int length) {
    byte[] reversed = new byte[bytes.length + 1];
    for (int index = 0; index < bytes.length; index++) {
      reversed[bytes.length - index] = bytes[index];
    }
    write(new BigInteger(reversed), length);
  }

  byte[] finish(int length) {
    return Arrays.copyOf(data, length);
  }
}
