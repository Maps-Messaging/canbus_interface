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
package io.mapsmessaging.canbus.j1939;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** MSG-210: literal wire identifiers and validation at the PDU boundary. */
class CanIdBuilderBoundaryTest {

  @Test
  void build_valid_boundaries_match_wire_identifiers_and_round_trip() {
    // PGN, priority, source, destination, wire identifier, decoded destination.
    int[][] cases = {
        {0x000000, 0, 0, 0, 0x00000000, 0},
        {0x00EA00, 6, 42, 77, 0x18EA4D2A, 77},
        {0x00EE00, 6, 255, 255, 0x18EEFFFF, 255},
        {0x00EF00, 7, 255, 255, 0x1CEFFFFF, 255},
        {0x01EF00, 0, 0, 0, 0x01EF0000, 0},
        {0x00F000, 0, 0, 77, 0x00F00000, 255},
        {0x00F0FF, 7, 255, 0, 0x1CF0FFFF, 255},
        {0x01F112, 6, 42, 77, 0x19F1122A, 255},
        {0x01FFFF, 7, 255, 255, 0x1DFFFFFF, 255}
    };
    for (int[] row : cases) {
      int identifier = CanIdBuilder.build(row[0], row[1], row[2], row[3]);
      String context = "PGN 0x" + Integer.toHexString(row[0]);
      assertEquals(row[4], identifier, context);
      CanId decoded = CanId.parse(identifier);
      assertAll(context,
          () -> assertEquals(row[0], decoded.getPgn()),
          () -> assertEquals(row[1], decoded.getPriority()),
          () -> assertEquals(row[2], decoded.getSourceAddress()),
          () -> assertEquals(row[5], decoded.getDestinationAddress()),
          () -> assertEquals(identifier, CanIdBuilder.build(decoded.getPgn(), decoded.getPriority(),
              decoded.getSourceAddress(), decoded.getDestinationAddress())));
    }
  }

  @Test
  void build_pdu2_ignores_each_valid_destination() {
    for (int destination = 0; destination <= 255; destination++) {
      assertEquals(0x19F1122A, CanIdBuilder.build(0x01F112, 6, 42, destination));
    }
  }

  @Test
  void build_pdu1_rejects_nonzero_pgn_low_byte() {
    for (int pgn : new int[]{0x00EA01, 0x00EFFF, 0x01EF01}) {
      assertThrows(IllegalArgumentException.class, () -> CanIdBuilder.build(pgn, 6, 42, 77),
          "PDU1 PGN 0x" + Integer.toHexString(pgn));
    }
  }

  @Test
  void build_rejects_priority_outside_three_bits() {
    for (int priority : new int[]{Integer.MIN_VALUE, -1, 8, Integer.MAX_VALUE}) {
      assertThrows(IllegalArgumentException.class, () -> CanIdBuilder.build(0x00EA00, priority, 42, 77),
          "priority " + priority);
    }
  }

  @Test
  void build_rejects_source_address_outside_one_byte() {
    for (int address : new int[]{Integer.MIN_VALUE, -1, 256, Integer.MAX_VALUE}) {
      assertThrows(IllegalArgumentException.class, () -> CanIdBuilder.build(0x00EA00, 6, address, 77),
          "source " + address);
    }
  }

  @Test
  void build_rejects_destination_address_outside_one_byte() {
    for (int address : new int[]{Integer.MIN_VALUE, -1, 256, Integer.MAX_VALUE}) {
      assertThrows(IllegalArgumentException.class, () -> CanIdBuilder.build(0x00EA00, 6, 42, address),
          "destination " + address);
    }
  }

  @Test
  void build_rejects_out_of_range_pgn_instead_of_aliasing_valid_pgn() {
    // Bit 18 is outside the J1939 PGN space; bit 17 is not exercised here.
    for (int pgn : new int[]{-1, 0x40000, Integer.MAX_VALUE}) {
      assertThrows(IllegalArgumentException.class, () -> CanIdBuilder.build(pgn, 6, 42, 77),
          "PGN " + pgn);
    }
  }
}
