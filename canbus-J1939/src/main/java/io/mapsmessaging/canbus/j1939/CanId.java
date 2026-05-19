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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CanId {

  private static final int EXTENDED_ID_MASK = 0x1FFFFFFF;

  private static final int PRIORITY_SHIFT = 26;
  private static final int PRIORITY_MASK = 0x7;

  private static final int DATA_PAGE_SHIFT = 24;
  private static final int DATA_PAGE_MASK = 0x1;

  private static final int PDU_FORMAT_SHIFT = 16;
  private static final int PDU_SPECIFIC_SHIFT = 8;
  private static final int BYTE_MASK = 0xFF;

  private static final int PDU1_MAX_PDU_FORMAT = 239;
  private static final int BROADCAST_ADDRESS = 255;

  private final int priority;
  private final int pgn;
  private final int sourceAddress;
  private final int destinationAddress;
  private final int pduFormat;
  private final int groupExtension;

  public static CanId parse(int canIdentifier) {
    int identifier = canIdentifier & EXTENDED_ID_MASK;

    int priority = (identifier >> PRIORITY_SHIFT) & PRIORITY_MASK;
    int dataPage = (identifier >> DATA_PAGE_SHIFT) & DATA_PAGE_MASK;
    int pduFormat = (identifier >> PDU_FORMAT_SHIFT) & BYTE_MASK;
    int pduSpecific = (identifier >> PDU_SPECIFIC_SHIFT) & BYTE_MASK;
    int sourceAddress = identifier & BYTE_MASK;

    int pgn;
    int destinationAddress;
    int groupExtension;

    if (pduFormat <= PDU1_MAX_PDU_FORMAT) {
      destinationAddress = pduSpecific;
      groupExtension = -1;
      pgn = (dataPage << 16) | (pduFormat << 8);
    } else {
      destinationAddress = BROADCAST_ADDRESS;
      groupExtension = pduSpecific;
      pgn = (dataPage << 16) | (pduFormat << 8) | groupExtension;
    }

    return new CanId(
        priority,
        pgn,
        sourceAddress,
        destinationAddress,
        pduFormat,
        groupExtension);
  }

  public boolean isPdu1() {
    return pduFormat <= PDU1_MAX_PDU_FORMAT;
  }

  public boolean isPdu2() {
    return !isPdu1();
  }

  public boolean isBroadcast() {
    return isPdu2() || (isPdu1() && destinationAddress == BROADCAST_ADDRESS);
  }

  public Integer getDestinationAddressOrNull() {
    if (isPdu1()) {
      return destinationAddress;
    }
    return null;
  }

  public Integer getGroupExtensionOrNull() {
    if (isPdu2()) {
      return groupExtension;
    }
    return null;
  }
}