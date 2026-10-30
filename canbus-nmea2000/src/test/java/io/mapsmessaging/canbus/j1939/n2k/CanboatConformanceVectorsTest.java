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
 */
package io.mapsmessaging.canbus.j1939.n2k;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.mapsmessaging.canbus.j1939.CanIdBuilder;
import io.mapsmessaging.canbus.j1939.n2k.codec.N2kMessageParser;
import io.mapsmessaging.canbus.j1939.n2k.framing.FrameHandler;
import io.mapsmessaging.canbus.j1939.n2k.framing.message.KnownMessage;
import io.mapsmessaging.canbus.j1939.n2k.framing.message.Message;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import static org.junit.jupiter.api.Assertions.*;

/** Pinned external byte/value oracles; expectations never come from the codec under test. */
class CanboatConformanceVectorsTest {
  private static JsonArray vectors;
  private static Map<String, N2kMessageParser> parsers;

  @BeforeAll
  static void load_vectors_and_real_registries() throws Exception {
    try (InputStream stream = CanboatConformanceVectorsTest.class.getResourceAsStream("/conformance/canboat-vectors.json")) {
      assertNotNull(stream);
      JsonObject corpus = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
      assertEquals("5b8340403e009b935f6a398b1ef0244a03e3d128", corpus.get("upstreamCommit").getAsString());
      vectors = corpus.getAsJsonArray("vectors");
    }
    parsers = Map.of("nmea2000", new N2kMessageParser(N2kParserFactory.getN2kParser()),
        "j1939", new N2kMessageParser(N2kParserFactory.getJ1939Parser()));
  }

  @Test
  void corpus_has_no_empty_or_duplicate_oracles() {
    assertEquals(72, vectors.size());
    List<String> identifiers = stream().map(vector -> vector.get("id").getAsString()).toList();
    assertEquals(identifiers.size(), identifiers.stream().distinct().count());
    for (JsonObject vector : stream().toList()) {
      if (!vector.has("reject")) {
        assertFalse(vector.getAsJsonObject("expected").isEmpty(), vector.get("id").getAsString());
      }
      assertTrue(vector.get("line").getAsInt() > 0);
      assertTrue(vector.get("upstreamInput").getAsString().endsWith(".in"));
      assertTrue(vector.get("upstreamOutput").getAsString().endsWith(".out"));
    }
  }

  @TestFactory
  Stream<DynamicTest> decoding_matches_independent_canboat_values() {
    return stream().map(vector -> DynamicTest.dynamicTest(vector.get("id").getAsString(), () -> {
      N2kMessageParser parser = parser(vector);
      if (vector.has("reject")) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> parser.decodeToJson(pgn(vector), payload(vector)));
        String expectedReason = vector.has("rejectionMessage") ? vector.get("rejectionMessage").getAsString() : "minimum";
        assertTrue(failure.getMessage().contains(expectedReason), failure.getMessage());
      } else {
        assertEnvelope(vector, parser.decodeToJson(pgn(vector), payload(vector)));
      }
    }));
  }

  @TestFactory
  Stream<DynamicTest> independently_specified_values_encode_to_known_wire_bytes() {
    List<String> identifiers = List.of("pgn-unit-coverage:3", "pgn-unit-coverage:5", "pgn-si-units:5",
        "pgn-bitlookup-64:1", "pgn-bitlookup-64:2", "pgn-bitlookup-64:3", "j1939-unit-coverage:7");
    return identifiers.stream().map(id -> DynamicTest.dynamicTest(id, () -> {
      JsonObject vector = stream().filter(row -> row.get("id").getAsString().equals(id)).findFirst().orElseThrow();
      JsonObject envelope = new JsonObject();
      envelope.addProperty("name", vector.get("name").getAsString());
      envelope.add("packet", vector.getAsJsonObject("expected").deepCopy());
      assertArrayEquals(payload(vector), parser(vector).encodeFromJson(pgn(vector), envelope));
    }));
  }

  @TestFactory
  Stream<DynamicTest> decimal_addresses_encode_to_the_independent_wire_prefix() {
    return stream().filter(vector -> vector.get("id").getAsString().startsWith("pgn-decimal:") && vector.has("derivation") && !vector.has("reject"))
        .map(vector -> DynamicTest.dynamicTest(vector.get("id").getAsString(), () -> {
          JsonObject envelope = new JsonObject();
          JsonObject packet = new JsonObject();
          packet.add("dscMessageAddress", vector.getAsJsonObject("expected").get("dscMessageAddress"));
          envelope.addProperty("name", vector.get("name").getAsString());
          envelope.add("packet", packet);
          byte[] encoded = parser(vector).encodeFromJson(pgn(vector), envelope);
          assertArrayEquals(Arrays.copyOfRange(payload(vector), 2, 7), Arrays.copyOfRange(encoded, 2, 7));
        }));
  }

  @TestFactory
  Stream<DynamicTest> literal_single_and_fast_frames_decode_to_independent_values() {
    return stream().filter(vector -> !vector.has("reject"))
        .filter(vector -> vector.get("protocol").getAsString().equals("nmea2000"))
        .map(vector -> DynamicTest.dynamicTest(vector.get("id").getAsString(), () -> {
          FrameHandler handler = new FrameHandler(parser(vector));
          int identifier = CanIdBuilder.build(pgn(vector), vector.get("priority").getAsInt(),
              vector.get("sourceAddress").getAsInt(), vector.get("destinationAddress").getAsInt());
          byte[] payload = payload(vector);
          Optional<Message> completed = Optional.empty();
          if (vector.has("frames")) {
            JsonArray frames = vector.getAsJsonArray("frames");
            for (int index = 0; index < frames.size(); index++) {
              byte[] frame = HexFormat.of().parseHex(frames.get(index).getAsString());
              completed = handler.onFrame(identifier, true, frame.length, frame);
              if (index < frames.size() - 1) {
                assertTrue(completed.isEmpty(), "Premature completion at frame " + index);
              }
            }
          } else if (vector.get("transport").getAsString().equals("Single")) {
            completed = handler.onFrame(identifier, true, payload.length, payload);
          } else {
            assertEquals("Fast", vector.get("transport").getAsString());
            // Construct the specified wire headers directly, independently of FramePacker.
            int consumed = 0;
            int frameIndex = 0;
            while (consumed < payload.length) {
              byte[] frame = new byte[8];
              Arrays.fill(frame, (byte) 0xFF);
              frame[0] = (byte) (0xA0 | frameIndex);
              int offset = frameIndex == 0 ? 2 : 1;
              if (frameIndex == 0) {
                frame[1] = (byte) payload.length;
              }
              int count = Math.min(8 - offset, payload.length - consumed);
              System.arraycopy(payload, consumed, frame, offset, count);
              consumed += count;
              completed = handler.onFrame(identifier, true, 8, frame);
              if (consumed < payload.length) {
                assertTrue(completed.isEmpty(), "Premature completion at frame " + frameIndex);
              }
              frameIndex++;
            }
          }
          assertTrue(completed.isPresent(), "No completed message");
          KnownMessage message = assertInstanceOf(KnownMessage.class, completed.orElseThrow());
          assertArrayEquals(payload, message.getPayload());
          assertEnvelope(vector, message.getDecoded());
        }));
  }

  @Test
  void truncated_variable_string_is_rejected_instead_of_reading_past_payload() {
    // LAU length says seven bytes, but only three are present.
    assertThrows(IllegalArgumentException.class, () -> parsers.get("nmea2000")
        .decodeToJson(126998, HexFormat.of().parseHex("070168")));
  }

  @Test
  void truncated_route_repeat_and_inconsistent_count_are_rejected() {
    JsonObject vector = stream().filter(row -> row.get("id").getAsString().equals("pgn-string-encoding:1"))
        .findFirst().orElseThrow();
    byte[] wire = payload(vector);
    assertThrows(IllegalArgumentException.class,
        () -> parser(vector).decodeToJson(129285, Arrays.copyOf(wire, wire.length - 1)));
    wire[2] = 3; // nItems now claims a third waypoint, but only two are supplied.
    assertThrows(IllegalArgumentException.class, () -> parser(vector).decodeToJson(129285, wire));
  }

  private static void assertEnvelope(JsonObject vector, JsonObject actual) {
    assertNotNull(actual);
    assertEquals(vector.get("name").getAsString(), actual.get("name").getAsString());
    assertValues(vector.getAsJsonObject("expected"), actual.getAsJsonObject("packet"),
        vector.getAsJsonObject("tolerances"), "");
  }

  private static void assertValues(JsonElement expected, JsonElement actual, JsonObject tolerances, String path) {
    assertNotNull(actual, "Missing " + path);
    if (expected.isJsonObject()) {
      assertTrue(actual.isJsonObject(), path);
      for (Map.Entry<String, JsonElement> field : expected.getAsJsonObject().entrySet()) {
        assertValues(field.getValue(), actual.getAsJsonObject().get(field.getKey()), tolerances, path + field.getKey() + "/");
      }
    } else if (expected.isJsonArray()) {
      assertTrue(actual.isJsonArray(), path);
      assertEquals(expected.getAsJsonArray().size(), actual.getAsJsonArray().size(), path);
      for (int index = 0; index < expected.getAsJsonArray().size(); index++) {
        assertValues(expected.getAsJsonArray().get(index), actual.getAsJsonArray().get(index), tolerances, path + index + "/");
      }
    } else if (!expected.isJsonNull() && expected.getAsJsonPrimitive().isNumber()) {
      assertTrue(actual.isJsonPrimitive() && actual.getAsJsonPrimitive().isNumber(), path);
      String key = path.substring(0, path.length() - 1);
      BigDecimal tolerance = tolerances.has(key) ? tolerances.get(key).getAsBigDecimal() : BigDecimal.ZERO;
      BigDecimal difference = expected.getAsBigDecimal().subtract(actual.getAsBigDecimal()).abs();
      assertTrue(difference.compareTo(tolerance) <= 0,
          () -> path + " expected " + expected + " but was " + actual + ", tolerance " + tolerance);
    } else {
      assertEquals(expected, actual, path);
    }
  }

  private static Stream<JsonObject> stream() {
    return java.util.stream.StreamSupport.stream(vectors.spliterator(), false).map(JsonElement::getAsJsonObject);
  }

  private static N2kMessageParser parser(JsonObject vector) {
    return parsers.get(vector.get("protocol").getAsString());
  }

  private static int pgn(JsonObject vector) {
    return vector.get("pgn").getAsInt();
  }

  private static byte[] payload(JsonObject vector) {
    return HexFormat.of().parseHex(vector.get("hex").getAsString());
  }
}
