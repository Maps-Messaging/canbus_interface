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

import io.mapsmessaging.canbus.j1939.CanId;
import io.mapsmessaging.canbus.j1939.CanIdBuilder;

/** Builds and parses J1939/NMEA 2000 identifiers without payload definitions or CAN hardware. */
public final class J1939IdentifierExample {

  private J1939IdentifierExample() {
  }

  public static void main(String[] args) {
    if (args.length != 0) {
      throw new IllegalArgumentException("Usage: J1939IdentifierExample");
    }
    int pdu1Identifier = CanIdBuilder.build(0x00EA00, 6, 0x80, 0x23);
    int pdu2Identifier = CanIdBuilder.build(0x01F801, 2, 0x42, 0xFF);
    print("PDU1", pdu1Identifier);
    print("PDU2", pdu2Identifier);

    CanId pdu1 = CanId.parse(pdu1Identifier);
    CanId pdu2 = CanId.parse(pdu2Identifier);
    if (pdu1.getPgn() != 0x00EA00 || pdu1.getDestinationAddress() != 0x23 || !pdu1.isPdu1()) {
      throw new IllegalStateException("PDU1 identifier round trip failed");
    }
    if (pdu2.getPgn() != 0x01F801 || pdu2.getGroupExtension() != 1 || !pdu2.isBroadcast()) {
      throw new IllegalStateException("PDU2 identifier round trip failed");
    }
    int globalPdu1 = CanIdBuilder.build(0x00EA00, 6, 0x80, 0xFF);
    if (!CanId.parse(globalPdu1).isBroadcast()) {
      throw new IllegalStateException("PDU1 global destination was not recognized");
    }
    System.out.println("PDU1 destination 255 is broadcast: true");
  }

  private static void print(String label, int identifier) {
    CanId decoded = CanId.parse(identifier);
    System.out.printf("%s identifier=0x%08X priority=%d pgn=0x%05X source=%d destination=%d%n",
        label, identifier, decoded.getPriority(), decoded.getPgn(), decoded.getSourceAddress(),
        decoded.getDestinationAddress());
    System.out.printf("  pdu1=%s pdu2=%s broadcast=%s destinationOrNull=%s groupExtensionOrNull=%s%n",
        decoded.isPdu1(), decoded.isPdu2(), decoded.isBroadcast(),
        decoded.getDestinationAddressOrNull(), decoded.getGroupExtensionOrNull());
  }
}
