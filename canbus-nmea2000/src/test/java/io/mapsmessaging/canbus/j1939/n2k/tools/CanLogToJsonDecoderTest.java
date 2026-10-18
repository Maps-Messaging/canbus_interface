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
package io.mapsmessaging.canbus.j1939.n2k.tools;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import io.mapsmessaging.canbus.j1939.n2k.framing.FrameHandler;
import io.mapsmessaging.canbus.j1939.n2k.framing.message.UnknownMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Constructor;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CanLogToJsonDecoderTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void reportsMalformedLinesAndUnknownFramesAsValidJson() throws Exception {
    FrameHandler handler = mock(FrameHandler.class);
    when(handler.onFrame(anyInt(), anyBoolean(), anyInt(), any(byte[].class)))
        .thenAnswer(call -> Optional.of(UnknownMessage.invalidFrame(
            "unrecognized frame", call.getArgument(0), call.getArgument(2), call.getArgument(3))));

    JsonArray events = decode(handler, "bad candump line", "(1.25) can0 09F80101#0102030405060708");

    assertEquals(2, events.size());
    assertEquals("invalidLine", events.get(0).getAsJsonObject().get("eventType").getAsString());
    JsonObject unknown = events.get(1).getAsJsonObject();
    assertEquals("unknownMessage", unknown.get("eventType").getAsString());
    assertEquals(2, unknown.get("lineNumber").getAsInt());
    assertEquals(1, unknown.get("sourceAddress").getAsInt());
  }

  @Test
  void reportsAnIncompleteFastPacketAtEndOfFile() throws Exception {
    JsonArray events = decode(emptyHandler(), "(1.25) can0 09F01401#0008010203040506");

    assertEquals(1, events.size());
    JsonObject incomplete = events.get(0).getAsJsonObject();
    assertEquals("incompleteFastPacket", incomplete.get("eventType").getAsString());
    assertEquals(126996, incomplete.get("pgn").getAsInt());
    assertEquals(2, incomplete.get("expectedFrameCount").getAsInt());
    assertEquals(1, incomplete.get("receivedFrameCount").getAsInt());
  }

  @Test
  void reportsOrphanContinuation() throws Exception {
    JsonArray events = decode(emptyHandler(), "(1.25) can0 09F01401#010708090A0B0C0D");

    assertEquals(1, events.size());
    assertEquals("orphanFastPacketContinuation",
        events.get(0).getAsJsonObject().get("eventType").getAsString());
    assertEquals(1, events.get(0).getAsJsonObject().get("frameIndex").getAsInt());
  }

  @Test
  void completedFastPacketProducesNoIncompleteEvent() throws Exception {
    JsonArray events = decode(emptyHandler(),
        "(1.25) can0 09F01401#0008010203040506",
        "(1.26) can0 09F01401#010708090A0B0C0D");

    assertEquals(0, events.size());
  }

  @Test
  void rejectsAnOddLengthPayloadAsAnInvalidLine() throws Exception {
    JsonArray events = decode(emptyHandler(), "(1.25) can0 09F80101#123");

    assertEquals(1, events.size());
    assertEquals("invalidLine", events.get(0).getAsJsonObject().get("eventType").getAsString());
    assertFalse(events.get(0).getAsJsonObject().get("decoded").getAsBoolean());
  }

  @Test
  void candumpFramesComparePayloadContent() throws Exception {
    Class<?> frameClass = Class.forName(CanLogToJsonDecoder.class.getName() + "$CandumpFrame");
    Constructor<?> constructor = frameClass.getDeclaredConstructor(
        long.class, double.class, String.class, int.class, byte[].class);
    constructor.setAccessible(true);
    Object first = constructor.newInstance(1L, 1.25, "can0", 0x09F80101, new byte[]{1, 2});
    Object same = constructor.newInstance(1L, 1.25, "can0", 0x09F80101, new byte[]{1, 2});
    Object different = constructor.newInstance(1L, 1.25, "can0", 0x09F80101, new byte[]{1, 3});

    assertEquals(first, same);
    assertEquals(first.hashCode(), same.hashCode());
    assertNotEquals(first, different);
    org.junit.jupiter.api.Assertions.assertTrue(first.toString().contains("[1, 2]"));
  }

  @Test
  void emitsDecodedPayloadFromKnownMessage() throws Exception {
    FrameHandler handler = mock(FrameHandler.class);
    JsonObject payload = new JsonObject();
    payload.addProperty("heading", 42);
    when(handler.onFrame(anyInt(), anyBoolean(), anyInt(), any(byte[].class)))
        .thenReturn(Optional.of(new io.mapsmessaging.canbus.j1939.n2k.framing.message.KnownMessage(
            io.mapsmessaging.canbus.j1939.CanId.parse(0x09F80101),
            0x09F80101, new byte[]{1}, payload)));

    JsonArray events = decode(handler, "(1.25) can0 09F80101#01");

    assertEquals(1, events.size());
    JsonObject decoded = events.get(0).getAsJsonObject();
    assertEquals("decodedMessage", decoded.get("eventType").getAsString());
    assertEquals(42, decoded.getAsJsonObject("data").get("heading").getAsInt());
  }

  @Test
  void reportsFrameHandlerFailureWithoutLosingFollowingFrames() throws Exception {
    FrameHandler handler = mock(FrameHandler.class);
    when(handler.onFrame(anyInt(), anyBoolean(), anyInt(), any(byte[].class)))
        .thenThrow(new IllegalStateException("bad frame"));

    JsonArray events = decode(handler,
        "(1.25) can0 09F80101#01",
        "(1.26) can0 09F80101#02");

    assertEquals(2, events.size());
    assertEquals("frameHandlerError", events.get(0).getAsJsonObject().get("eventType").getAsString());
    assertEquals("bad frame", events.get(0).getAsJsonObject().get("error").getAsString());
    assertEquals(2, events.get(1).getAsJsonObject().get("lineNumber").getAsInt());
  }

  @Test
  void sequenceChangeReportsIncompletePacketAndOrphanContinuation() throws Exception {
    JsonArray events = decode(emptyHandler(),
        "(1.25) can0 09F01401#0008010203040506",
        "(1.26) can0 09F01401#210708090A0B0C0D");

    assertEquals(2, events.size());
    assertEquals("incompleteFastPacket", events.get(0).getAsJsonObject().get("eventType").getAsString());
    assertEquals("Fast-packet sequence changed before packet completed",
        events.get(0).getAsJsonObject().get("reason").getAsString());
    assertEquals("orphanFastPacketContinuation",
        events.get(1).getAsJsonObject().get("eventType").getAsString());
  }

  @Test
  void missingFrameIndexReportsGapAndOrphanContinuation() throws Exception {
    JsonArray events = decode(emptyHandler(),
        "(1.25) can0 09F01401#0008010203040506",
        "(1.26) can0 09F01401#020708090A0B0C0D");

    assertEquals(2, events.size());
    assertEquals("Fast-packet frame index gap",
        events.get(0).getAsJsonObject().get("reason").getAsString());
    assertEquals(2, events.get(1).getAsJsonObject().get("frameIndex").getAsInt());
  }

  @Test
  void newPacketStartReportsPreviousPacketAsIncomplete() throws Exception {
    JsonArray events = decode(emptyHandler(),
        "(1.25) can0 09F01401#0008010203040506",
        "(1.26) can0 09F01401#2008010203040506");

    assertEquals(2, events.size());
    assertEquals("New fast-packet start before previous packet completed",
        events.get(0).getAsJsonObject().get("reason").getAsString());
    assertEquals("End of file before fast-packet completed",
        events.get(1).getAsJsonObject().get("reason").getAsString());
    assertEquals(1, events.get(1).getAsJsonObject().get("sequenceIdentifier").getAsInt());
  }

  private FrameHandler emptyHandler() {
    FrameHandler handler = mock(FrameHandler.class);
    when(handler.onFrame(anyInt(), anyBoolean(), anyInt(), any(byte[].class)))
        .thenReturn(Optional.empty());
    return handler;
  }

  private JsonArray decode(FrameHandler handler, String... lines) throws Exception {
    Path input = temporaryDirectory.resolve("can.log");
    Path output = temporaryDirectory.resolve("events.json");
    Files.write(input, java.util.List.of(lines));
    new CanLogToJsonDecoder(handler).decode(input, output);
    return new GsonBuilder().setStrictness(Strictness.STRICT).create()
        .fromJson(Files.readString(output), JsonArray.class);
  }
}
