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

import io.mapsmessaging.canbus.device.SocketCanDevice;
import io.mapsmessaging.canbus.device.frames.CanFrame;
import java.io.IOException;

/** Linux SocketCAN example: receives one frame by default; transmission is explicit and restricted to vcan0. */
public final class SocketCanExample {

  private SocketCanExample() {
  }

  public static void main(String[] arguments) throws IOException {
    if (arguments.length > 2 || (arguments.length == 2 && !"--send-vcan".equals(arguments[1]))) {
      throw new IllegalArgumentException("Usage: SocketCanExample [interface] [--send-vcan]");
    }
    String interfaceName = arguments.length == 0 ? "vcan0" : arguments[0];
    boolean send = arguments.length == 2;
    if (send && !"vcan0".equals(interfaceName)) {
      throw new IllegalArgumentException("This example transmits only on the vcan0 virtual interface");
    }
    try (SocketCanDevice device = new SocketCanDevice(interfaceName)) {
      System.out.println("Capabilities: " + device.getCanCapabilities());
      if (send) {
        // Observe this on another socket, for example with candump vcan0.
        device.writeFrame(new CanFrame(0x123, false, 2, new byte[] {0x12, 0x34}));
        System.out.println("Sent one classic frame on vcan0");
      } else {
        System.out.println("Waiting for one frame on " + interfaceName + " (blocking read; no receive timeout)");
        CanFrame received = device.readFrame();
        System.out.println(received);
      }
    }
  }
}
