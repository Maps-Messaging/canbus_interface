/* * * Copyright [ 2020 - 2024 ] Matthew Buckton * Copyright [ 2024 - 2026 ] MapsMessaging B.V. * * Licensed under the Apache License, Version 2.0 with the Commons Clause * (the "License"); you may not use this file except in compliance with the License. * You may obtain a copy of the License at: * * http://www.apache.org/licenses/LICENSE-2.0 * https://commonsclause.com/ * * Unless required by applicable law or agreed to in writing, software * distributed under the License is distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. * See the License for the specific language governing permissions and * limitations under the License. */ package io.mapsmessaging.canbus.device.frames;

import lombok.Getter;

import java.util.Arrays;

public record CanFrame(int canIdentifier, boolean extendedFrame, int dataLengthCode, byte[] data) {

  public CanFrame(int canIdentifier, boolean extendedFrame, int dataLengthCode, byte[] data) {
    this.canIdentifier = canIdentifier;
    this.extendedFrame = extendedFrame;
    this.dataLengthCode = dataLengthCode;
    this.data = data == null ? null : Arrays.copyOf(data, data.length);
  }

  @Override
  public byte[] data() {
    return data == null ? null : Arrays.copyOf(data, data.length);
  }

  public static CanFrame fromBytes(byte[] raw) {
    if (raw == null || raw.length < 13) {
      throw new IllegalArgumentException("Raw CAN frame must be at least 13 bytes");
    }
    int canIdentifier =
        ((raw[0] & 0xFF) << 24)
            | ((raw[1] & 0xFF) << 16)
            | ((raw[2] & 0xFF) << 8)
            | (raw[3] & 0xFF);
    int flags = raw[4] & 0xFF;
    boolean extendedFrame = (flags & 0x01) != 0;
    int dataLengthCode = (flags >>> 1) & 0x0F;
    byte[] data = new byte[dataLengthCode];
    if (dataLengthCode > 0) {
      System.arraycopy(raw, 5, data, 0, dataLengthCode);
    }
    return new CanFrame(canIdentifier, extendedFrame, dataLengthCode, data);
  }


  public byte[] getRawData() {
    int payloadLength = data == null ? 0 : Math.min(data.length, 8);
    byte[] raw = new byte[13];
    raw[0] = (byte) ((canIdentifier >>> 24) & 0xFF);
    raw[1] = (byte) ((canIdentifier >>> 16) & 0xFF);
    raw[2] = (byte) ((canIdentifier >>> 8) & 0xFF);
    raw[3] = (byte) (canIdentifier & 0xFF);
    int flags = 0;
    if (extendedFrame) {
      flags |= 0x01;
    }
    flags |= (dataLengthCode & 0x0F) << 1;
    raw[4] = (byte) (flags & 0xFF);
    if (payloadLength > 0) {
      System.arraycopy(data, 0, raw, 5, payloadLength);
    }
    return raw;
  }
}