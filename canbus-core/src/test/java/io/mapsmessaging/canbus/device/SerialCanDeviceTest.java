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

package io.mapsmessaging.canbus.device;

import io.mapsmessaging.canbus.device.codec.CanFrameStreamCodec;
import io.mapsmessaging.canbus.device.codec.SlcanCanFrameStreamCodec;
import io.mapsmessaging.canbus.device.frames.CanFrame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerialCanDeviceTest {

  @Test
  void closeReleasesBothStreamsWhenCodecFails() throws IOException {
    IOException codecFailure = new IOException("codec close");
    boolean[] closed = new boolean[2];
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]) {
      @Override
      public void close() throws IOException {
        closed[0] = true;
        super.close();
      }
    };
    ByteArrayOutputStream output = new ByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        closed[1] = true;
        super.close();
      }
    };
    CanFrameStreamCodec codec = new CanFrameStreamCodec() {
      @Override
      public CanFrame read(InputStream stream) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void write(OutputStream stream, CanFrame frame) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void close(InputStream in, OutputStream out) throws IOException {
        throw codecFailure;
      }
    };

    SerialCanDevice device = new SerialCanDevice("can0", input, output, codec);

    assertSame(codecFailure, assertThrows(IOException.class, device::close));
    assertTrue(closed[0]);
    assertTrue(closed[1]);
  }

  @Test
  void closeRetainsFirstFailureAndSuppressesLaterFailures() throws IOException {
    IOException inputFailure = new IOException("input close");
    IOException outputFailure = new IOException("output close");
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]) {
      @Override
      public void close() throws IOException {
        throw inputFailure;
      }
    };
    ByteArrayOutputStream output = new ByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        throw outputFailure;
      }
    };

    SerialCanDevice device = new SerialCanDevice("can0", input, output, new SlcanCanFrameStreamCodec());

    IOException thrown = assertThrows(IOException.class, device::close);
    assertSame(inputFailure, thrown);
    assertEquals(1, thrown.getSuppressed().length);
    assertSame(outputFailure, thrown.getSuppressed()[0]);
  }
}
