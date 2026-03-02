

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
package io.mapsmessaging.canbus.device;

import io.mapsmessaging.canbus.device.frames.CanFrame;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class Vcan0ReadWriteDemo {

  private static final int CAN_ID = 0x123;
  private static final int PAYLOAD_LENGTH = 8;

  public static void main(String[] args) throws Exception {
    String interfaceName = args != null && args.length > 0 ? args[0] : "vcan0";

    AtomicBoolean running = new AtomicBoolean(true);
    AtomicLong sentCount = new AtomicLong(0);

    try (SocketCanDevice reader = new SocketCanDevice(interfaceName);
         SocketCanDevice writer = new SocketCanDevice(interfaceName)) {

      System.out.println("Opened reader on " + interfaceName + " capabilities=" + reader.getCanCapabilities());
      System.out.println("Opened writer on " + interfaceName + " capabilities=" + writer.getCanCapabilities());
      System.out.println("Press Ctrl+C to stop.");

      Thread readThread = new Thread(() -> runReadLoop(reader, running), "vcan-read");
      Thread writeThread = new Thread(() -> runWriteLoop(writer, running, sentCount), "vcan-write");

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        running.set(false);
        System.out.println("Shutdown requested...");
      }, "shutdown-hook"));

      readThread.start();
      writeThread.start();

      readThread.join();
      writeThread.join();
    }
  }

  private static void runReadLoop(SocketCanDevice reader, AtomicBoolean running) {
    Instant lastPrint = Instant.now();
    long received = 0;

    while (running.get()) {
      try {
        CanFrame frame = reader.readFrame();
        received++;

        String payloadHex = toHex(frame.getData(), frame.getDataLengthCode());
        System.out.println("RX id=0x" + Integer.toHexString(frame.getCanIdentifier())
            + " len=" + frame.getDataLengthCode()
            + " data=" + payloadHex);

        Instant now = Instant.now();
        if (Duration.between(lastPrint, now).toSeconds() >= 5) {
          System.out.println("RX stats: received=" + received);
          lastPrint = now;
        }
      } catch (IOException e) {
        if (running.get()) {
          System.err.println("RX error: " + e.getMessage());
          e.printStackTrace(System.err);
        }
        running.set(false);
      }
    }
  }

  private static void runWriteLoop(SocketCanDevice writer, AtomicBoolean running, AtomicLong sentCount) {
    long counter = 0;

    while (running.get()) {
      try {
        byte[] payload = new byte[PAYLOAD_LENGTH];

        payload[0] = (byte) (counter & 0xFF);
        payload[1] = (byte) ((counter >> 8) & 0xFF);
        payload[2] = (byte) ((counter >> 16) & 0xFF);
        payload[3] = (byte) ((counter >> 24) & 0xFF);

        payload[4] = (byte) 0xAA;
        payload[5] = (byte) 0x55;
        payload[6] = (byte) 0xCC;
        payload[7] = (byte) 0x33;

        writer.writeFrame(new CanFrame(CAN_ID, false, PAYLOAD_LENGTH, payload));

        long sent = sentCount.incrementAndGet();
        if ((sent % 20) == 0) {
          System.out.println("TX stats: sent=" + sent);
        }

        counter++;
        Thread.sleep(250);
      } catch (IOException e) {
        if (running.get()) {
          System.err.println("TX error: " + e.getMessage());
          e.printStackTrace(System.err);
        }
        running.set(false);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        running.set(false);
      }
    }
  }

  private static String toHex(byte[] data, int length) {
    if (data == null) {
      return "";
    }
    int safeLength = Math.min(Math.max(length, 0), data.length);

    StringBuilder builder = new StringBuilder(safeLength * 2);
    for (int i = 0; i < safeLength; i++) {
      int value = data[i] & 0xFF;
      if (value < 0x10) {
        builder.append('0');
      }
      builder.append(Integer.toHexString(value));
      if (i + 1 < safeLength) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }
}