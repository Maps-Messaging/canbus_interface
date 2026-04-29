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
package io.mapsmessaging.canbus.device;

import io.mapsmessaging.canbus.device.codec.CanFrameStreamCodec;
import io.mapsmessaging.canbus.device.frames.CanFrame;
import lombok.Getter;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class SerialCanDevice implements CanDevice, Closeable {

  private final InputStream inputStream;
  private final OutputStream outputStream;
  private final CanFrameStreamCodec canFrameStreamCodec;

  @Getter
  private final String deviceName;

  public SerialCanDevice(String deviceName,
                         InputStream inputStream,
                         OutputStream outputStream,
                         CanFrameStreamCodec canFrameStreamCodec) throws IOException {
    if (deviceName == null || deviceName.isBlank()) {
      throw new IllegalArgumentException("deviceName must not be null or blank");
    }
    if (inputStream == null) {
      throw new IllegalArgumentException("inputStream must not be null");
    }
    if (outputStream == null) {
      throw new IllegalArgumentException("outputStream must not be null");
    }
    if (canFrameStreamCodec == null) {
      throw new IllegalArgumentException("canFrameStreamCodec must not be null");
    }

    this.deviceName = deviceName.trim();
    this.inputStream = inputStream;
    this.outputStream = outputStream;
    this.canFrameStreamCodec = canFrameStreamCodec;

    this.canFrameStreamCodec.initialise(this.inputStream, this.outputStream);
  }

  public CanFrame readFrame() throws IOException {
    return canFrameStreamCodec.read(inputStream);
  }

  public void writeFrame(CanFrame canFrame) throws IOException {
    if (canFrame == null) {
      throw new IllegalArgumentException("canFrame must not be null");
    }
    canFrameStreamCodec.write(outputStream, canFrame);
  }

  @Override
  public void flush() throws IOException {
    canFrameStreamCodec.flush(outputStream);
  }

  @Override
  public CanCapabilities getCanCapabilities() {
    return canFrameStreamCodec.getCanCapabilities();
  }

  @Override
  public void close() throws IOException {
    IOException closeException = null;

    try {
      canFrameStreamCodec.close(inputStream, outputStream);
    } catch (IOException e) {
      closeException = e;
    }

    try {
      inputStream.close();
    } catch (IOException e) {
      if (closeException == null) {
        closeException = e;
      } else {
        closeException.addSuppressed(e);
      }
    }

    try {
      outputStream.close();
    } catch (IOException e) {
      if (closeException == null) {
        closeException = e;
      } else {
        closeException.addSuppressed(e);
      }
    }

    if (closeException != null) {
      throw closeException;
    }
  }
}