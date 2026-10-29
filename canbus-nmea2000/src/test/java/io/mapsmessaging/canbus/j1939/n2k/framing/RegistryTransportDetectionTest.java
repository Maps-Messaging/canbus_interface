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
package io.mapsmessaging.canbus.j1939.n2k.framing;

import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.j1939.n2k.codec.N2kMessageParser;
import io.mapsmessaging.canbus.j1939.n2k.framing.message.KnownMessage;
import io.mapsmessaging.canbus.j1939.n2k.framing.message.Message;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiler;
import io.mapsmessaging.canbus.device.frames.CanFrame;
import io.mapsmessaging.canbus.j1939.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.canbus.j1939.n2k.model.N2kTransportType;
import io.mapsmessaging.canbus.j1939.n2k.parser.N2kXmlDialectParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** MSG-212: transport metadata must survive parsing/compilation and control wire framing. */
class RegistryTransportDetectionTest {

  @Test
  void registry_fixture_looks_up_single_and_fast_pgns_and_unknown() throws Exception {
    N2kCompiledRegistry registry = registry();
    for (int pgn : new int[]{59904, 60928, 126993, 126996, 126998}) {
      assertNotNull(registry.getRequiredMessage(pgn), "PGN " + pgn);
    }
    assertNull(registry.getRequiredMessage(130001));
  }

  @Test
  void single_frame_metadata_preserves_payload_and_identifier() throws Exception {
    FramePacker packer = new FramePacker(new N2kMessageParser(registry()));
    int[][] cases = {{59904, 3, 0x18EA4D2A}, {60928, 8, 0x18EE4D2A}, {126993, 8, 0x19F0112A}};
    for (int[] row : cases) {
      List<CanFrame> frames = packer.pack(row[0], 6, 42, 77, envelope());
      byte[] expected = new byte[row[1]];
      Arrays.fill(expected, (byte) 0xFF);
      assertEquals(1, frames.size(), "PGN " + row[0]);
      assertEquals(row[2], frames.get(0).canIdentifier());
      assertEquals(row[1], frames.get(0).dataLengthCode());
      assertArrayEquals(expected, frames.get(0).data());
    }
  }

  @Test
  void known_fast_packet_metadata_frames_product_and_short_configuration() throws Exception {
    FramePacker packer = new FramePacker(new N2kMessageParser(registry()));
    assertFastPacket(packer.pack(126996, 6, 42, 77, envelope()), 134, 0x19F0142A);
    // A short fast-packet payload still carries a fast-packet header.
    assertFastPacket(packer.pack(126998, 6, 42, 77, envelope()), 3, 0x19F0162A);
  }

  @Test
  void changing_only_registry_transport_changes_framing_for_same_pgn_and_length() throws Exception {
    List<CanFrame> single = new FramePacker(new N2kMessageParser(registry(130000, "Single", 3)))
        .pack(130000, 6, 42, 77, envelope());
    List<CanFrame> fast = new FramePacker(new N2kMessageParser(registry(130000, "Fast", 3)))
        .pack(130000, 6, 42, 77, envelope());
    assertArrayEquals(new byte[]{-1, -1, -1}, single.get(0).data());
    assertFastPacket(fast, 3, 0x19FBD02A);
  }

  @Test
  void single_frame_metadata_rejects_oversize_instead_of_guessing_fast_packet() throws Exception {
    FramePacker packer = new FramePacker(new N2kMessageParser(registry(130000, "Single", 9)));
    assertThrows(IllegalArgumentException.class, () -> packer.pack(130000, 6, 42, 77, envelope()));
  }

  @Test
  void unsupported_transport_protocol_is_rejected_instead_of_guessed_from_length() throws Exception {
    FramePacker packer = new FramePacker(new N2kMessageParser(registry(130000, "ISO", 9)));
    RuntimeException failure = assertThrows(RuntimeException.class,
        () -> packer.pack(130000, 6, 42, 77, envelope()));
    assertTrue(failure instanceof IllegalArgumentException || failure instanceof UnsupportedOperationException,
        "Expected an explicit unsupported transport rejection: " + failure);
  }

  @Test
  void bundled_registry_transport_metadata_matches_required_pgns() throws Exception {
    N2kCompiledRegistry registry = N2kCompiler.compile(N2kXmlDialectParser.parseFromClasspath());
    for (int pgn : new int[]{59904, 60928, 126993}) {
      assertEquals(N2kTransportType.SINGLE_FRAME, registry.getTransportType(pgn, 8));
      assertThrows(IllegalArgumentException.class, () -> registry.getTransportType(pgn, 9));
    }
    for (int pgn : new int[]{126996, 126998}) {
      assertEquals(N2kTransportType.FAST_PACKET, registry.getTransportType(pgn, 3));
    }
  }

  @Test
  void unknown_pgn_fallback_is_limited_to_single_frame_payloads() throws Exception {
    N2kCompiledRegistry registry = registry();
    for (int length : new int[]{0, 1, 8}) {
      assertEquals(N2kTransportType.SINGLE_FRAME, registry.getTransportType(130001, length));
    }
    assertThrows(IllegalArgumentException.class, () -> registry.getTransportType(130001, 9));
  }

  @Test
  void unsupported_type_is_rejected_even_for_short_payloads() throws Exception {
    FramePacker packer = new FramePacker(new N2kMessageParser(registry(130000, "ISO", 3)));
    assertThrows(UnsupportedOperationException.class, () -> packer.pack(130000, 6, 42, 77, envelope()));
    N2kCompiledRegistry registry = registry(130000, "Unrecognised", 3);
    assertThrows(UnsupportedOperationException.class, () -> registry.getTransportType(130000, 3));
  }

  @Test
  void receiver_reassembles_short_fast_packets_instead_of_decoding_headers() throws Exception {
    for (int length : new int[]{1, 3, 4, 6, 7, 8}) {
      N2kMessageParser parser = new N2kMessageParser(registry(130000, "Fast", length));
      FrameHandler handler = new FrameHandler(parser);
      byte[] payload = new byte[length];
      for (int index = 0; index < length; index++) {
        payload[index] = (byte) (index + 1);
      }
      List<CanFrame> frames = new FramePacker(parser)
          .packFastPacket(130000, 0x19FBD02A, 42, 255, payload);
      for (int index = 0; index < frames.size(); index++) {
        CanFrame frame = frames.get(index);
        Optional<Message> result = handler.onFrame(frame.canIdentifier(), true, frame.dataLengthCode(), frame.data());
        if (index < frames.size() - 1) {
          assertTrue(result.isEmpty(), "Incomplete fast packet, length " + length);
        } else {
          KnownMessage message = assertInstanceOf(KnownMessage.class, result.orElseThrow());
          assertArrayEquals(payload, message.getPayload(), "Fast packet length " + length);
        }
      }
    }
  }

  @Test
  void receiver_preserves_inverter_ac_instance_from_short_fast_packet() throws Exception {
    N2kMessageParser parser = new N2kMessageParser(N2kCompiler.compile(N2kXmlDialectParser.parseFromClasspath()));
    FrameHandler handler = new FrameHandler(parser);
    byte[] frame = {0, 4, 1, 0, 3, 0x54, 0, 0};

    KnownMessage message = assertInstanceOf(KnownMessage.class,
        handler.onFrame(0x19F2152A, true, 8, frame).orElseThrow());

    assertArrayEquals(new byte[]{1, 0, 3, 0x54}, message.getPayload());
    assertEquals(0, message.getDecoded().getAsJsonObject("packet").get("acInstance").getAsInt());
  }

  @Test
  void receiver_keeps_single_frame_payload_that_resembles_fast_packet_header() throws Exception {
    FrameHandler handler = new FrameHandler(new N2kMessageParser(registry()));
    byte[] payload = {0, 9, 1};

    KnownMessage message = assertInstanceOf(KnownMessage.class,
        handler.onFrame(0x18EA4D2A, true, 3, payload).orElseThrow());

    assertArrayEquals(payload, message.getPayload());
  }

  private static void assertFastPacket(List<CanFrame> frames, int payloadLength, int identifier) {
    int expectedCount = 1 + (Math.max(0, payloadLength - 6) + 6) / 7;
    assertEquals(expectedCount, frames.size());
    byte[] payload = new byte[payloadLength];
    int offset = 0;
    int sequence = frames.get(0).data()[0] & 0xE0;
    for (int index = 0; index < frames.size(); index++) {
      CanFrame frame = frames.get(index);
      assertEquals(identifier, frame.canIdentifier());
      assertEquals(8, frame.dataLengthCode());
      assertEquals(sequence | index, frame.data()[0] & 0xFF);
      int start = index == 0 ? 2 : 1;
      if (index == 0) {
        assertEquals(payloadLength, frame.data()[1] & 0xFF);
      }
      int count = Math.min(8 - start, payloadLength - offset);
      System.arraycopy(frame.data(), start, payload, offset, count);
      offset += count;
    }
    byte[] expected = new byte[payloadLength];
    Arrays.fill(expected, (byte) 0xFF);
    assertArrayEquals(expected, payload);
  }

  private static JsonObject envelope() {
    JsonObject json = new JsonObject();
    json.add("packet", new JsonObject());
    return json;
  }

  private static N2kCompiledRegistry registry() throws Exception {
    return parse(definition(59904, "Single", 3) + definition(60928, "Single", 8)
        + definition(126993, "Single", 8) + definition(126996, "Fast", 134)
        + definition(126998, "Fast", 3));
  }

  private static N2kCompiledRegistry registry(int pgn, String type, int length) throws Exception {
    return parse(definition(pgn, type, length));
  }

  private static N2kCompiledRegistry parse(String definitions) throws Exception {
    String xml = "<PGNDefinitions><PGNs>" + definitions + "</PGNs></PGNDefinitions>";
    try (ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
      List<N2kMessageDefinition> parsed = N2kXmlDialectParser.parse(input);
      return N2kCompiler.compile(parsed);
    }
  }

  private static String definition(int pgn, String type, int length) {
    // Deliberately no fields: encoding emits a deterministic fixed-length 0xFF payload.
    return "<PGNInfo><PGN>" + pgn + "</PGN><Id>fixture" + pgn + "</Id>"
        + "<Priority>6</Priority><Type>" + type + "</Type><Complete>true</Complete>"
        + "<Length>" + length + "</Length><Fields/></PGNInfo>";
  }
}
