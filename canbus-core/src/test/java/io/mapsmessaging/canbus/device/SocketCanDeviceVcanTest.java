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

import io.mapsmessaging.canbus.device.frames.CanFrame;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SocketCanDeviceVcanTest {

  @Test
  void opensReadsStatusAndWritesClassicFrameWhenVcan0Exists() throws Exception {
    Assumptions.assumeTrue(Files.isDirectory(Path.of("/sys/class/net/vcan0")),
        "vcan0 is not present on this test server");

    try (SocketCanDevice device = new SocketCanDevice("vcan0")) {
      assertNotNull(device.getCanCapabilities());
      assertEquals("vcan0", device.readInterfaceStatus().getInterfaceName());
      device.writeFrame(new CanFrame(0x123, false, 2, new byte[]{0x12, 0x34}));
    }
  }
}
