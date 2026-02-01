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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanIdBuilderTest {

  @Test
  void build_pdu1_pfLessThan240_usesDestinationAddressInPs() {
    int pgn = 0x00EA00; // PF=0xEA (234) < 240, DP=0
    int priority = 3;
    int sourceAddress = 0x22;
    int destinationAddress = 0x55;

    int id = CanIdBuilder.build(pgn, priority, sourceAddress, destinationAddress);

    assertEquals(priority & 0x07, extractPriority(id));
    assertEquals(0, extractDp(id));
    assertEquals(0xEA, extractPf(id));
    assertEquals(destinationAddress & 0xFF, extractPs(id));
    assertEquals(sourceAddress & 0xFF, extractSa(id));
    assertEquals(id, id & 0x1FFFFFFF);
  }

  @Test
  void build_pdu2_pfGreaterOrEqual240_usesPgnLowByteInPs() {
    int pgn = 0x01F112; // PF=0xF1 (241) >= 240, DP=1, PGN low byte=0x12
    int priority = 6;
    int sourceAddress = 0xAB;
    int destinationAddress = 0x55; // must be ignored for PDU2

    int id = CanIdBuilder.build(pgn, priority, sourceAddress, destinationAddress);

    assertEquals(priority & 0x07, extractPriority(id));
    assertEquals(1, extractDp(id));
    assertEquals(0xF1, extractPf(id));
    assertEquals(0x12, extractPs(id));
    assertEquals(sourceAddress & 0xFF, extractSa(id));
    assertEquals(id, id & 0x1FFFFFFF);
  }

  @Test
  void build_priorityIsMaskedTo3Bits() {
    int pgn = 0x00EA00; // PF < 240 so destination is used
    int priority = 0xFF; // should become 7
    int sourceAddress = 0x01;
    int destinationAddress = 0x02;

    int id = CanIdBuilder.build(pgn, priority, sourceAddress, destinationAddress);

    assertEquals(7, extractPriority(id));
  }

  @Test
  void build_sourceAndDestinationAreMaskedTo8Bits() {
    int pgn = 0x00EA00; // PF < 240 => destination used
    int priority = 1;
    int sourceAddress = 0x123;       // low byte = 0x23
    int destinationAddress = 0x1FE;  // low byte = 0xFE

    int id = CanIdBuilder.build(pgn, priority, sourceAddress, destinationAddress);

    assertEquals(0x23, extractSa(id));
    assertEquals(0xFE, extractPs(id));
  }

  @Test
  void build_dpComesFromPgnBit16() {
    int priority = 2;
    int sourceAddress = 0x10;
    int destinationAddress = 0x20;

    int pgnDp0 = 0x00EA00; // DP=0
    int pgnDp1 = 0x01EA00; // DP=1 (bit 16 set)

    int idDp0 = CanIdBuilder.build(pgnDp0, priority, sourceAddress, destinationAddress);
    int idDp1 = CanIdBuilder.build(pgnDp1, priority, sourceAddress, destinationAddress);

    assertEquals(0, extractDp(idDp0));
    assertEquals(1, extractDp(idDp1));
  }

  @Test
  void build_alwaysReturns29BitIdentifier() {
    int pgn = 0x01FFFF; // max-ish within 18-bit PGN space; DP=1, PF=0xFF
    int priority = 7;
    int sourceAddress = 0xFF;
    int destinationAddress = 0xFF;

    int id = CanIdBuilder.build(pgn, priority, sourceAddress, destinationAddress);

    assertTrue((id & ~0x1FFFFFFF) == 0, "identifier must be limited to 29 bits");
  }

  private static int extractPriority(int identifier) {
    return (identifier >> 26) & 0x07;
  }

  private static int extractDp(int identifier) {
    return (identifier >> 24) & 0x01;
  }

  private static int extractPf(int identifier) {
    return (identifier >> 16) & 0xFF;
  }

  private static int extractPs(int identifier) {
    return (identifier >> 8) & 0xFF;
  }

  private static int extractSa(int identifier) {
    return identifier & 0xFF;
  }
}
