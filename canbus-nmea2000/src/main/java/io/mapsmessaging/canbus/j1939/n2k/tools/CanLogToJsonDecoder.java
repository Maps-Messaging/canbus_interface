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

package io.mapsmessaging.canbus.j1939.n2k.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.j1939.n2k.codec.N2kMessageParser;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiler;
import io.mapsmessaging.canbus.j1939.n2k.framing.FrameHandler;
import io.mapsmessaging.canbus.j1939.n2k.framing.message.KnownMessage;
import io.mapsmessaging.canbus.j1939.n2k.framing.message.Message;
import io.mapsmessaging.canbus.j1939.n2k.framing.message.UnknownMessage;
import io.mapsmessaging.canbus.j1939.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.canbus.j1939.n2k.parser.N2kXmlDialectParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CanLogToJsonDecoder {

  private static final Pattern CANDUMP_PATTERN = Pattern.compile(
      "^\\((?<timestamp>[0-9]+\\.[0-9]+)\\)\\s+(?<interfaceName>\\S+)\\s+(?<canIdentifier>[0-9A-Fa-f]{8})#(?<payload>[0-9A-Fa-f]*)$"
  );

  private static final HexFormat HEX_FORMAT = HexFormat.of().withUpperCase();

  private final FrameHandler frameHandler;
  private final Gson gson;
  private final Map<FastPacketKey, FastPacketState> fastPacketStates;

  public CanLogToJsonDecoder(FrameHandler frameHandler) {
    this.frameHandler = frameHandler;
    this.gson = new GsonBuilder()
        .disableHtmlEscaping()
        .create();
    this.fastPacketStates = new HashMap<>();
  }

  public void decode(Path inputPath, Path outputPath) throws IOException {
    try (
        BufferedReader reader = Files.newBufferedReader(inputPath);
        BufferedWriter writer = Files.newBufferedWriter(outputPath)
    ) {
      String line;
      long lineNumber = 0;
      writer.write("[\n");
      while ((line = reader.readLine()) != null) {
        lineNumber++;

        Optional<CandumpFrame> optionalFrame = parseLine(lineNumber, line);
        if (optionalFrame.isEmpty()) {
          writeInvalidLine(writer, lineNumber, line);
          continue;
        }

        CandumpFrame frame = optionalFrame.get();

        trackFastPacket(writer, frame);
        handleFrame(writer, frame);
      }
      flushIncompleteFastPackets(writer);
      writer.write("]");
    }

  }

  private Optional<CandumpFrame> parseLine(long lineNumber, String line) {
    Matcher matcher = CANDUMP_PATTERN.matcher(line.trim());
    if (!matcher.matches()) {
      return Optional.empty();
    }

    try {
      double timestamp = Double.parseDouble(matcher.group("timestamp"));
      String interfaceName = matcher.group("interfaceName");
      int canIdentifier = (int) Long.parseUnsignedLong(matcher.group("canIdentifier"), 16);
      byte[] payload = HEX_FORMAT.parseHex(matcher.group("payload"));

      return Optional.of(new CandumpFrame(
          lineNumber,
          timestamp,
          interfaceName,
          canIdentifier,
          payload
      ));
    } catch (RuntimeException exception) {
      return Optional.empty();
    }
  }

  private void trackFastPacket(BufferedWriter writer, CandumpFrame frame) throws IOException {
    if (frame.payload().length != 8) {
      return;
    }

    CanIdentifierFields identifierFields = decodeIdentifier(frame.canIdentifier());

    if (!isFastPacketPgn(identifierFields.parameterGroupNumber())) {
      return;
    }

    int frameHeader = frame.payload()[0] & 0xff;
    int sequenceIdentifier = (frameHeader >> 5) & 0x7;
    int frameIndex = frameHeader & 0x1f;

    if (frameIndex == 0) {
      handleFastPacketStart(writer, frame, identifierFields, sequenceIdentifier);
      return;
    }

    handleFastPacketContinuation(writer, frame, identifierFields, sequenceIdentifier, frameIndex);
  }

  private boolean isFastPacketPgn(int parameterGroupNumber) {
    return switch (parameterGroupNumber) {
      case 126996, // Product Information
           126998, // Configuration Information
           127489, // Engine Parameters Dynamic
           127496, // Trip Parameters Vessel
           127497, // Trip Parameters Engine
           128275, // Distance Log
           129029, // GNSS Position Data
           129038, // AIS Class A Position Report
           129039, // AIS Class B Position Report
           129040, // AIS Class B Extended Position Report
           129041, // AIS Aids to Navigation Report
           129284, // Navigation Data
           129285, // Navigation Route / WP Information
           129540  // GNSS Sats in View
          -> true;
      default -> false;
    };
  }

  private void handleFastPacketStart(
      BufferedWriter writer,
      CandumpFrame frame,
      CanIdentifierFields identifierFields,
      int sequenceIdentifier
  ) throws IOException {
    int totalPayloadLength = frame.payload()[1] & 0xff;

    if (totalPayloadLength <= 6) {
      return;
    }

    FastPacketKey key = new FastPacketKey(
        identifierFields.parameterGroupNumber(),
        identifierFields.sourceAddress(),
        identifierFields.destinationAddress()
    );

    FastPacketState existingState = fastPacketStates.remove(key);
    if (existingState != null && !existingState.isComplete()) {
      writeIncompleteFastPacket(
          writer,
          existingState,
          "New fast-packet start before previous packet completed"
      );
    }

    int expectedFrameCount = calculateExpectedFrameCount(totalPayloadLength);

    FastPacketState state = new FastPacketState(
        key,
        sequenceIdentifier,
        totalPayloadLength,
        expectedFrameCount,
        frame.lineNumber(),
        frame.timestamp(),
        frame.interfaceName(),
        frame.canIdentifier()
    );

    state.addFrame(0, frame.lineNumber());
    fastPacketStates.put(key, state);
  }

  private void handleFastPacketContinuation(
      BufferedWriter writer,
      CandumpFrame frame,
      CanIdentifierFields identifierFields,
      int sequenceIdentifier,
      int frameIndex
  ) throws IOException {
    FastPacketKey key = new FastPacketKey(
        identifierFields.parameterGroupNumber(),
        identifierFields.sourceAddress(),
        identifierFields.destinationAddress()
    );

    FastPacketState state = fastPacketStates.get(key);
    if (state == null) {
      writeOrphanFastPacketContinuation(
          writer,
          frame,
          identifierFields,
          sequenceIdentifier,
          frameIndex
      );
      return;
    }

    if (state.sequenceIdentifier() != sequenceIdentifier) {
      fastPacketStates.remove(key);
      writeIncompleteFastPacket(
          writer,
          state,
          "Fast-packet sequence changed before packet completed"
      );
      writeOrphanFastPacketContinuation(
          writer,
          frame,
          identifierFields,
          sequenceIdentifier,
          frameIndex
      );
      return;
    }

    if (frameIndex != state.nextExpectedFrameIndex()) {
      fastPacketStates.remove(key);
      writeIncompleteFastPacket(
          writer,
          state,
          "Fast-packet frame index gap"
      );
      writeOrphanFastPacketContinuation(
          writer,
          frame,
          identifierFields,
          sequenceIdentifier,
          frameIndex
      );
      return;
    }

    state.addFrame(frameIndex, frame.lineNumber());

    if (state.isComplete()) {
      fastPacketStates.remove(key);
    }
  }

  private int calculateExpectedFrameCount(int totalPayloadLength) {
    int remainingBytes = totalPayloadLength - 6;
    int continuationFrames = (remainingBytes + 6) / 7;

    return 1 + continuationFrames;
  }

  private CanIdentifierFields decodeIdentifier(int canIdentifier) {
    int priority = (canIdentifier >> 26) & 0x7;
    int dataPage = (canIdentifier >> 24) & 0x1;
    int pduFormat = (canIdentifier >> 16) & 0xff;
    int pduSpecific = (canIdentifier >> 8) & 0xff;
    int sourceAddress = canIdentifier & 0xff;

    int parameterGroupNumber;
    int destinationAddress;

    if (pduFormat < 240) {
      parameterGroupNumber = (dataPage << 16) | (pduFormat << 8);
      destinationAddress = pduSpecific;
    } else {
      parameterGroupNumber = (dataPage << 16) | (pduFormat << 8) | pduSpecific;
      destinationAddress = 255;
    }

    return new CanIdentifierFields(
        priority,
        parameterGroupNumber,
        sourceAddress,
        destinationAddress
    );
  }

  private void handleFrame(BufferedWriter writer, CandumpFrame frame) throws IOException {
    Optional<Message> optionalMessage;

    try {
      optionalMessage = frameHandler.onFrame(
          frame.canIdentifier(),
          true,
          frame.payload().length,
          frame.payload()
      );
    } catch (RuntimeException exception) {
      writeFrameHandlerError(writer, frame, exception);
      return;
    }

    if (optionalMessage.isEmpty()) {
      return;
    }

    Message message = optionalMessage.get();

    if (message instanceof KnownMessage knownMessage) {
      writeKnownMessage(writer, frame, knownMessage);
      return;
    }

    if (message instanceof UnknownMessage unknownMessage) {
      writeUnknownMessage(writer, frame, unknownMessage);
      return;
    }

    writeUnexpectedMessage(writer, frame, message);
  }

  private void flushIncompleteFastPackets(BufferedWriter writer) throws IOException {
    for (FastPacketState state : fastPacketStates.values()) {
      if (!state.isComplete()) {
        writeIncompleteFastPacket(
            writer,
            state,
            "End of file before fast-packet completed"
        );
      }
    }

    fastPacketStates.clear();
  }

  private void writeKnownMessage(
      BufferedWriter writer,
      CandumpFrame frame,
      KnownMessage knownMessage
  ) throws IOException {
    JsonObject output = createBaseFrameJson(frame);

    output.addProperty("eventType", "decodedMessage");
    output.addProperty("decoded", true);
    output.add("data", knownMessage.getDecoded());

    writer.write(gson.toJson(output));
    writer.write(",");
    writer.newLine();
  }

  private void writeUnknownMessage(
      BufferedWriter writer,
      CandumpFrame frame,
      UnknownMessage unknownMessage
  ) throws IOException {
    JsonObject output = createBaseFrameJson(frame);

    output.addProperty("eventType", "unknownMessage");
    output.addProperty("decoded", false);
    output.addProperty("reason", unknownMessage.getReason().toString());
    output.addProperty("detail", unknownMessage.getDetail());

    writer.write(gson.toJson(output));
    writer.write(",");
    writer.newLine();
  }

  private void writeIncompleteFastPacket(
      BufferedWriter writer,
      FastPacketState state,
      String reason
  ) throws IOException {
    JsonObject output = new JsonObject();

    output.addProperty("eventType", "incompleteFastPacket");
    output.addProperty("decoded", false);
    output.addProperty("reason", reason);
    output.addProperty("startLineNumber", state.startLineNumber());
    output.addProperty("lastLineNumber", state.lastLineNumber());
    output.addProperty("timestamp", state.startTimestamp());
    output.addProperty("interface", state.interfaceName());
    output.addProperty("canId", String.format("%08X", state.canIdentifier()));
    output.addProperty("pgn", state.key().parameterGroupNumber());
    output.addProperty("pgnHex", String.format("0x%05X", state.key().parameterGroupNumber()));
    output.addProperty("sourceAddress", state.key().sourceAddress());
    output.addProperty("destinationAddress", state.key().destinationAddress());
    output.addProperty("sequenceIdentifier", state.sequenceIdentifier());
    output.addProperty("payloadLength", state.totalPayloadLength());
    output.addProperty("expectedFrameCount", state.expectedFrameCount());
    output.addProperty("receivedFrameCount", state.receivedFrameCount());
    output.addProperty("nextExpectedFrameIndex", state.nextExpectedFrameIndex());

    writer.write(gson.toJson(output));
    writer.write(",");
    writer.newLine();
  }

  private void writeOrphanFastPacketContinuation(
      BufferedWriter writer,
      CandumpFrame frame,
      CanIdentifierFields identifierFields,
      int sequenceIdentifier,
      int frameIndex
  ) throws IOException {
    JsonObject output = createBaseFrameJson(frame);

    output.addProperty("eventType", "orphanFastPacketContinuation");
    output.addProperty("decoded", false);
    output.addProperty("pgn", identifierFields.parameterGroupNumber());
    output.addProperty("pgnHex", String.format("0x%05X", identifierFields.parameterGroupNumber()));
    output.addProperty("sourceAddress", identifierFields.sourceAddress());
    output.addProperty("destinationAddress", identifierFields.destinationAddress());
    output.addProperty("sequenceIdentifier", sequenceIdentifier);
    output.addProperty("frameIndex", frameIndex);
    output.addProperty("reason", "Fast-packet continuation without matching start frame");

    writer.write(gson.toJson(output));
    writer.write(",");
    writer.newLine();
  }

  private void writeUnexpectedMessage(
      BufferedWriter writer,
      CandumpFrame frame,
      Message message
  ) throws IOException {
    JsonObject output = createBaseFrameJson(frame);

    output.addProperty("eventType", "unexpectedMessage");
    output.addProperty("decoded", false);
    output.addProperty("messageType", message.getClass().getName());
    output.addProperty("error", "Unexpected message type returned by frame handler");

    writer.write(gson.toJson(output));
    writer.write(",");
    writer.newLine();
  }

  private void writeFrameHandlerError(
      BufferedWriter writer,
      CandumpFrame frame,
      RuntimeException exception
  ) throws IOException {
    JsonObject output = createBaseFrameJson(frame);

    output.addProperty("eventType", "frameHandlerError");
    output.addProperty("decoded", false);
    output.addProperty("error", exception.getMessage());

    writer.write(gson.toJson(output));
    writer.write(",");
    writer.newLine();
  }

  private void writeInvalidLine(
      BufferedWriter writer,
      long lineNumber,
      String line
  ) throws IOException {
    JsonObject output = new JsonObject();

    output.addProperty("eventType", "invalidLine");
    output.addProperty("lineNumber", lineNumber);
    output.addProperty("decoded", false);
    output.addProperty("error", "Invalid candump line");
    output.addProperty("line", line);

    writer.write(gson.toJson(output));
    writer.write(",");
    writer.newLine();
  }

  private JsonObject createBaseFrameJson(CandumpFrame frame) {
    CanIdentifierFields identifierFields = decodeIdentifier(frame.canIdentifier());

    JsonObject output = new JsonObject();

    output.addProperty("lineNumber", frame.lineNumber());
    output.addProperty("timestamp", frame.timestamp());
    output.addProperty("interface", frame.interfaceName());
    output.addProperty("canId", String.format("%08X", frame.canIdentifier()));
    output.addProperty("priority", identifierFields.priority());
    output.addProperty("pgn", identifierFields.parameterGroupNumber());
    output.addProperty("pgnHex", String.format("0x%05X", identifierFields.parameterGroupNumber()));
    output.addProperty("sourceAddress", identifierFields.sourceAddress());
    output.addProperty("destinationAddress", identifierFields.destinationAddress());
    output.addProperty("fromAddress", identifierFields.sourceAddress());
    output.addProperty("toAddress", identifierFields.destinationAddress());
    output.addProperty("broadcast", identifierFields.destinationAddress() == 255);
    output.addProperty("payloadHex", HEX_FORMAT.formatHex(frame.payload()));

    return output;
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: CandumpN2kJsonDecoder <input-can-log> <output-jsonl>");
      System.exit(1);
    }

    N2kCompiledRegistry registry = buildRegistry();
    N2kMessageParser parser = new N2kMessageParser(registry);
    FrameHandler frameHandler = new FrameHandler(parser);

    CanLogToJsonDecoder decoder = new CanLogToJsonDecoder(frameHandler);

    decoder.decode(
        Path.of(args[0]),
        Path.of(args[1])
    );
  }

  private record CandumpFrame(
      long lineNumber,
      double timestamp,
      String interfaceName,
      int canIdentifier,
      byte[] payload
  ) {
  }

  private record CanIdentifierFields(
      int priority,
      int parameterGroupNumber,
      int sourceAddress,
      int destinationAddress
  ) {
  }
  private record FastPacketKey(
      int parameterGroupNumber,
      int sourceAddress,
      int destinationAddress
  ) {
  }

  private static class FastPacketState {

    private final FastPacketKey key;
    private final int sequenceIdentifier;
    private final int totalPayloadLength;
    private final int expectedFrameCount;
    private final long startLineNumber;
    private final double startTimestamp;
    private final String interfaceName;
    private final int canIdentifier;

    private int receivedFrameCount;
    private int nextExpectedFrameIndex;
    private long lastLineNumber;

    FastPacketState(
        FastPacketKey key,
        int sequenceIdentifier,
        int totalPayloadLength,
        int expectedFrameCount,
        long startLineNumber,
        double startTimestamp,
        String interfaceName,
        int canIdentifier
    ) {
      this.key = key;
      this.sequenceIdentifier = sequenceIdentifier;
      this.totalPayloadLength = totalPayloadLength;
      this.expectedFrameCount = expectedFrameCount;
      this.startLineNumber = startLineNumber;
      this.startTimestamp = startTimestamp;
      this.interfaceName = interfaceName;
      this.canIdentifier = canIdentifier;
      this.receivedFrameCount = 0;
      this.nextExpectedFrameIndex = 0;
      this.lastLineNumber = startLineNumber;
    }

    void addFrame(int frameIndex, long lineNumber) {
      receivedFrameCount++;
      nextExpectedFrameIndex = frameIndex + 1;
      lastLineNumber = lineNumber;
    }

    boolean isComplete() {
      return receivedFrameCount >= expectedFrameCount;
    }

    FastPacketKey key() {
      return key;
    }

    int sequenceIdentifier() {
      return sequenceIdentifier;
    }

    int totalPayloadLength() {
      return totalPayloadLength;
    }

    int expectedFrameCount() {
      return expectedFrameCount;
    }

    long startLineNumber() {
      return startLineNumber;
    }

    double startTimestamp() {
      return startTimestamp;
    }

    String interfaceName() {
      return interfaceName;
    }

    int canIdentifier() {
      return canIdentifier;
    }

    int receivedFrameCount() {
      return receivedFrameCount;
    }

    int nextExpectedFrameIndex() {
      return nextExpectedFrameIndex;
    }

    long lastLineNumber() {
      return lastLineNumber;
    }
  }

  protected static N2kCompiledRegistry buildRegistry() throws Exception {
    List<N2kMessageDefinition> defs = N2kXmlDialectParser.parseFromClasspath();
    return N2kCompiler.compile(defs);
  }
}