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

package io.mapsmessaging.canbus.app;

import io.mapsmessaging.canbus.device.frames.CanFrame;
import io.mapsmessaging.canbus.device.SocketCanDevice;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

public final class CanDump {

  private static final int CAN_EFF_FLAG = 0x80000000;
  private static final int CAN_EFF_MASK = 0x1FFFFFFF;

  private static void write(SocketCanDevice writer, int canId, boolean extended)
      throws IOException {
    byte[] data = new byte[8];
    ThreadLocalRandom.current().nextBytes(data);

    int id = extended ? (canId | CAN_EFF_FLAG) : canId;

    CanFrame frame = new CanFrame(id, data.length, data);

    writer.writeFrame(frame);
  }

  private static void read(SocketCanDevice reader, String interfaceName) throws IOException {
    CanFrame frame = reader.readFrame();

    long nowNanos = System.nanoTime();
    String timestamp = Instant.now().toString();

    int rawId = frame.canIdentifier();
    boolean extended = (rawId & CAN_EFF_FLAG) != 0;
    int id = extended ? (rawId & CAN_EFF_MASK) : (rawId & 0x7FF);

    StringBuilder line = new StringBuilder(128);
    line.append(timestamp);
    line.append(" ");
    line.append(interfaceName);
    line.append(" ");
    line.append(extended ? String.format("%08X", id) : String.format("%03X", id));
    line.append(" [");
    line.append(frame.dataLengthCode());
    line.append("]");

    byte[] data = frame.data();
    int length = frame.dataLengthCode();
    for (int i = 0; i < length && i < 8; i++) {
      line.append(" ");
      line.append(String.format("%02X", data[i]));
    }

    line.append("  (t=");
    line.append(nowNanos);
    line.append("ns)");

    System.out.println(line);
  }

  public static void main(String[] args) throws Exception {
    String interfaceName = args.length > 0 ? args[0] : "can0";
    boolean startSend = args.length > 1;

    try (SocketCanDevice reader = new SocketCanDevice(interfaceName)) {
      while (true) {
        if (startSend) {
          System.err.println("Sending packet");
          write(reader, 0x123, false);
          Thread.sleep(50);
        }
        System.err.println("Reading packet");
        read(reader, interfaceName);
        Thread.sleep(50);
        startSend = true;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
