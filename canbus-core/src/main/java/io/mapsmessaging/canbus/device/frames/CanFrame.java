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

import java.util.Arrays;

public record CanFrame(int canIdentifier, boolean extendedFrame, int dataLengthCode, byte[] data) {

  private static final int RAW_FRAME_LENGTH = 13;
  private static final int MAX_DATA_LENGTH = 8;
  private static final int STANDARD_IDENTIFIER_MAX = 0x7FF;
  private static final int EXTENDED_IDENTIFIER_MAX = 0x1FFFFFFF;

  public CanFrame(int canIdentifier, boolean extendedFrame, int dataLengthCode, byte[] data) {
    validateIdentifier(canIdentifier, extendedFrame);
    validateDataLengthCode(dataLengthCode);
    validateData(dataLengthCode, data);

    this.canIdentifier = canIdentifier;
    this.extendedFrame = extendedFrame;
    this.dataLengthCode = dataLengthCode;
    this.data = dataLengthCode == 0 ? new byte[0] : Arrays.copyOf(data, dataLengthCode);
  }

  @Override
  public byte[] data() {
    return Arrays.copyOf(data, data.length);
  }

  public static CanFrame fromBytes(byte[] raw) {
    if (raw == null || raw.length != RAW_FRAME_LENGTH) {
      throw new IllegalArgumentException("Raw CAN frame must be exactly 13 bytes");
    }

    int canIdentifier =
        ((raw[0] & 0xFF) << 24)
            | ((raw[1] & 0xFF) << 16)
            | ((raw[2] & 0xFF) << 8)
            | (raw[3] & 0xFF);

    int flags = raw[4] & 0xFF;
    boolean extendedFrame = (flags & 0x01) != 0;
    int dataLengthCode = (flags >>> 1) & 0x0F;

    validateIdentifier(canIdentifier, extendedFrame);
    validateDataLengthCode(dataLengthCode);

    byte[] payload = new byte[dataLengthCode];
    if (dataLengthCode > 0) {
      System.arraycopy(raw, 5, payload, 0, dataLengthCode);
    }

    return new CanFrame(canIdentifier, extendedFrame, dataLengthCode, payload);
  }

  public byte[] getRawData() {
    byte[] raw = new byte[RAW_FRAME_LENGTH];

    raw[0] = (byte) ((canIdentifier >>> 24) & 0xFF);
    raw[1] = (byte) ((canIdentifier >>> 16) & 0xFF);
    raw[2] = (byte) ((canIdentifier >>> 8) & 0xFF);
    raw[3] = (byte) (canIdentifier & 0xFF);

    int flags = 0;
    if (extendedFrame) {
      flags |= 0x01;
    }
    flags |= (dataLengthCode & 0x0F) << 1;
    raw[4] = (byte) flags;

    if (dataLengthCode > 0) {
      System.arraycopy(data, 0, raw, 5, dataLengthCode);
    }

    return raw;
  }

  private static void validateIdentifier(int canIdentifier, boolean extendedFrame) {
    if (canIdentifier < 0) {
      throw new IllegalArgumentException("CAN identifier must not be negative");
    }

    if (extendedFrame) {
      if (canIdentifier > EXTENDED_IDENTIFIER_MAX) {
        throw new IllegalArgumentException("Extended CAN identifier must be <= 0x1FFFFFFF");
      }
    } else {
      if (canIdentifier > STANDARD_IDENTIFIER_MAX) {
        throw new IllegalArgumentException("Standard CAN identifier must be <= 0x7FF");
      }
    }
  }

  private static void validateDataLengthCode(int dataLengthCode) {
    if (dataLengthCode < 0 || dataLengthCode > MAX_DATA_LENGTH) {
      throw new IllegalArgumentException("CAN data length code must be between 0 and 8");
    }
  }

  private static void validateData(int dataLengthCode, byte[] data) {
    if (dataLengthCode == 0) {
      if (data != null && data.length > 0) {
        throw new IllegalArgumentException("Data must be empty when data length code is 0");
      }
      return;
    }

    if (data == null) {
      throw new IllegalArgumentException("Data must not be null when data length code is greater than 0");
    }

    if (data.length < dataLengthCode) {
      throw new IllegalArgumentException("Data length must be at least the data length code");
    }

    if (data.length > MAX_DATA_LENGTH) {
      throw new IllegalArgumentException("Data length must not exceed 8 bytes");
    }
  }
}