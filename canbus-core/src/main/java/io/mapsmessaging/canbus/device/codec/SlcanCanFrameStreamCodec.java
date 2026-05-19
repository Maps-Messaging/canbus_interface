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

package io.mapsmessaging.canbus.device.codec;

import io.mapsmessaging.canbus.device.frames.CanFrame;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SlcanCanFrameStreamCodec implements CanFrameStreamCodec {

  private static final int MAX_LINE_LENGTH = 64;

  @Override
  public CanFrame read(InputStream inputStream) throws IOException {
    if (inputStream == null) {
      throw new IllegalArgumentException("InputStream must not be null");
    }

    String line = readLine(inputStream);
    if (line == null) {
      return null;
    }

    String trimmed = line.trim();
    if (trimmed.isEmpty()) {
      return read(inputStream);
    }

    char frameType = trimmed.charAt(0);
    boolean extendedFrame;
    switch (frameType) {
      case 't':
        extendedFrame = false;
        break;

      case 'T':
        extendedFrame = true;
        break;

      default:
        throw new IOException("Unsupported SLCAN frame type: " + frameType);
    }

    int identifierLength = extendedFrame ? 8 : 3;
    int minimumLength = 1 + identifierLength + 1;
    if (trimmed.length() < minimumLength) {
      throw new IOException("Invalid SLCAN frame: " + trimmed);
    }

    final int canIdentifier;
    try {
      canIdentifier = Integer.parseUnsignedInt(
          trimmed.substring(1, 1 + identifierLength),
          16);
    } catch (NumberFormatException exception) {
      throw new IOException("Invalid SLCAN CAN identifier: " + trimmed, exception);
    }

    int dataLengthCode = Character.digit(trimmed.charAt(1 + identifierLength), 16);
    if (dataLengthCode < 0 || dataLengthCode > 8) {
      throw new IOException("Invalid SLCAN DLC: " + trimmed);
    }

    int expectedLength = 1 + identifierLength + 1 + (dataLengthCode * 2);
    if (trimmed.length() != expectedLength) {
      throw new IOException("Invalid SLCAN frame length: " + trimmed);
    }

    byte[] data = new byte[dataLengthCode];
    int offset = 1 + identifierLength + 1;
    for (int index = 0; index < dataLengthCode; index++) {
      int start = offset + (index * 2);
      String payloadByte = trimmed.substring(start, start + 2);

      try {
        data[index] = (byte) Integer.parseInt(payloadByte, 16);
      } catch (NumberFormatException exception) {
        throw new IOException("Invalid SLCAN payload byte: " + payloadByte, exception);
      }
    }

    return new CanFrame(canIdentifier, extendedFrame, dataLengthCode, data);
  }

  @Override
  public void write(OutputStream outputStream, CanFrame canFrame) throws IOException {
    if (outputStream == null) {
      throw new IllegalArgumentException("OutputStream must not be null");
    }
    if (canFrame == null) {
      throw new IllegalArgumentException("CanFrame must not be null");
    }

    StringBuilder stringBuilder = new StringBuilder();
    if (canFrame.extendedFrame()) {
      stringBuilder.append('T');
      stringBuilder.append(String.format("%08X", canFrame.canIdentifier()));
    } else {
      stringBuilder.append('t');
      stringBuilder.append(String.format("%03X", canFrame.canIdentifier()));
    }

    stringBuilder.append(Integer.toHexString(canFrame.dataLengthCode()).toUpperCase());

    byte[] data = canFrame.data();
    for (int index = 0; index < canFrame.dataLengthCode(); index++) {
      stringBuilder.append(String.format("%02X", data[index] & 0xFF));
    }

    stringBuilder.append('\r');
    outputStream.write(stringBuilder.toString().getBytes(StandardCharsets.US_ASCII));
    outputStream.flush();
  }

  public void open(OutputStream outputStream) throws IOException {
    writeCommand(outputStream, "O");
  }

  public void close(OutputStream outputStream) throws IOException {
    writeCommand(outputStream, "C");
  }

  public void setBitRate(OutputStream outputStream, SlcanBitRate slcanBitRate) throws IOException {
    if (slcanBitRate == null) {
      throw new IllegalArgumentException("SlcanBitRate must not be null");
    }
    writeCommand(outputStream, "S" + slcanBitRate.getCode());
  }

  private void writeCommand(OutputStream outputStream, String command) throws IOException {
    if (outputStream == null) {
      throw new IllegalArgumentException("OutputStream must not be null");
    }
    outputStream.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
    outputStream.flush();
  }

  private String readLine(InputStream inputStream) throws IOException {
    StringBuilder stringBuilder = new StringBuilder();

    while (true) {
      int value = inputStream.read();
      if (value == -1) {
        return stringBuilder.isEmpty() ? null : stringBuilder.toString();
      }

      if (value == '\r' || value == '\n') {
        if (!stringBuilder.isEmpty()) {
          return stringBuilder.toString();
        }
        continue;
      }

      stringBuilder.append((char) value);
      if (stringBuilder.length() > MAX_LINE_LENGTH) {
        throw new IOException("SLCAN line exceeded maximum length");
      }
    }
  }
}