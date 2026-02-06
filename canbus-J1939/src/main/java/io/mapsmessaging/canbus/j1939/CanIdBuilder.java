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
package io.mapsmessaging.canbus.j1939;

public class CanIdBuilder {

  private static final int EXTENDED_ID_MASK = 0x1FFFFFFF;
  private static final int PRIORITY_MASK = 0x07;
  private static final int BYTE_MASK = 0xFF;
  private static final int PDU1_MAX_PF = 239;

  private CanIdBuilder() {
  }

  /**
   * Build a 29-bit identifier in J1939/N2K style.
   *
   * Fields:
   * - Priority: bits 26..28
   * - DP: bit 24 (from PGN bit 16)
   * - PF: bits 16..23
   * - PS: bits 8..15 (dest for PDU1, or PGN low byte for PDU2)
   * - SA: bits 0..7
   *
   * PGN rules:
   * - PF < 240 => PDU1 => PS is destination, PGN low byte must be 0x00
   * - PF >= 240 => PDU2 => PS comes from PGN low byte, destination implied global
   */
  public static int build(int pgn, int priority, int sourceAddress, int destinationAddress) {
    if (priority < 0 || priority > PRIORITY_MASK) {
      throw new IllegalArgumentException("priority must be in range 0..7, got " + priority);
    }

    int prio = priority & PRIORITY_MASK;
    int dp = (pgn >> 16) & 0x01;
    int pf = (pgn >> 8) & BYTE_MASK;

    int ps;
    if (pf <= PDU1_MAX_PF) {
      if ((pgn & BYTE_MASK) != 0) {
        throw new IllegalArgumentException("PDU1 PGN must have low byte 0x00, got 0x" + Integer.toHexString(pgn));
      }
      ps = destinationAddress & BYTE_MASK;
    } else {
      ps = pgn & BYTE_MASK;
    }

    int sa = sourceAddress & BYTE_MASK;

    int identifier = 0;
    identifier |= (prio << 26);
    identifier |= (dp << 24);
    identifier |= (pf << 16);
    identifier |= (ps << 8);
    identifier |= sa;

    return identifier & EXTENDED_ID_MASK;
  }

}
