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

import io.mapsmessaging.canbus.device.CanCapabilities;
import io.mapsmessaging.canbus.device.CanInterfaceStatus;
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
    reader.readFrame();
  }

  public static void main(String[] args) throws Exception {
    String interfaceName = args.length > 0 ? args[0] : "can0";
    boolean startSend = args.length > 1;

    try (SocketCanDevice reader = new SocketCanDevice(interfaceName)) {
      CanCapabilities canCapabilities =  reader.getCanCapabilities();
      CanInterfaceStatus status =reader.readInterfaceStatus();
      System.err.println("CAN Capabilities: " + canCapabilities);
      System.err.println("CAN InterfaceStatus: " + status);
      long reportTime = System.currentTimeMillis()+1000;
      long count = 0;
      while (true) {
        count++;
        if (startSend) {
          write(reader, 0x123, false);
        }
        read(reader, interfaceName);
        startSend = true;
        if(System.currentTimeMillis() > reportTime){
          reportTime = System.currentTimeMillis()+1000;
          System.err.println("Sent:"+count);
          count = 0;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
