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
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.mapsmessaging.canbus.j1939;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CanIdTest {

  @Test
  void parse_pdu1_identifier() {
    // Priority = 3
    // DP = 0
    // PF = 0xEC (236 < 240 => PDU1)
    // PS = destination = 0x45
    // SA = 0x22
    int canId =
        (3 << 26) |
            (0 << 24) |
            (0xEC << 16) |
            (0x45 << 8) |
            0x22;

    CanId id = CanId.parse(canId);

    Assertions.assertEquals(3, id.getPriority());
    Assertions.assertEquals(0xEC00, id.getPgn());
    Assertions.assertEquals(0x22, id.getSourceAddress());
    Assertions.assertEquals(0x45, id.getDestinationAddress());
  }

  @Test
  void parse_pdu2_identifier() {
    // Priority = 6
    // DP = 1
    // PF = 0xF1 (241 >= 240 => PDU2)
    // PS = PGN low byte = 0x10
    // SA = 0xAB
    int canId =
        (6 << 26) |
            (1 << 24) |
            (0xF1 << 16) |
            (0x10 << 8) |
            0xAB;

    CanId id = CanId.parse(canId);

    Assertions.assertEquals(6, id.getPriority());
    Assertions.assertEquals(0x1F110, id.getPgn());
    Assertions.assertEquals(0xAB, id.getSourceAddress());
    Assertions.assertEquals(255, id.getDestinationAddress());
  }
}
