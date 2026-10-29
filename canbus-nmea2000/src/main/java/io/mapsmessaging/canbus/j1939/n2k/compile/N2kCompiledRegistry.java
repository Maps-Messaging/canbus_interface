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

package io.mapsmessaging.canbus.j1939.n2k.compile;

import io.mapsmessaging.canbus.j1939.n2k.model.N2kTransportType;
import java.util.Locale;
import java.util.Map;
import lombok.Value;

@Value
public class N2kCompiledRegistry {
  Map<Integer, N2kCompiledMessage> messagesByPgn;

  public N2kCompiledMessage getRequiredMessage(int pgn) {
    return messagesByPgn.get(pgn);
  }

  /** Resolve transport from registry metadata; only unknown short payloads have a fallback. */
  public N2kTransportType getTransportType(int pgn, int payloadLength) {
    if (payloadLength < 0) {
      throw new IllegalArgumentException("Negative payload length for PGN " + pgn);
    }
    N2kCompiledMessage message = getRequiredMessage(pgn);
    String type = message == null ? null : message.getType();
    N2kTransportType transport = N2kTransportType.SINGLE_FRAME;
    if (type != null && !type.isBlank()) {
      transport = switch (type.trim().toLowerCase(Locale.ROOT)) {
        case "single", "single_frame" -> N2kTransportType.SINGLE_FRAME;
        case "fast", "fast_packet" -> N2kTransportType.FAST_PACKET;
        case "iso", "transport_protocol" -> N2kTransportType.TRANSPORT_PROTOCOL;
        default -> throw new UnsupportedOperationException("Unsupported transport '" + type + "' for PGN " + pgn);
      };
    }
    if (transport == N2kTransportType.SINGLE_FRAME && payloadLength > 8) {
      throw new IllegalArgumentException("PGN " + pgn + " requires single-frame payload length <= 8, got " + payloadLength);
    }
    return transport;
  }

}
