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

package io.mapsmessaging.canbus.j1939.n2k.codec;

import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledField;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledMessage;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.canbus.j1939.n2k.model.N2kFieldType;
import java.util.Arrays;

import io.mapsmessaging.canbus.j1939.n2k.model.N2kMessageLengthType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class N2kMessageParser {

  private static final java.util.EnumMap<N2kFieldType, Processor> PROCESSORS =
      new java.util.EnumMap<>(N2kFieldType.class);

  static {
    PROCESSORS.put(N2kFieldType.NUMBER, new NumericProcessor());
    PROCESSORS.put(N2kFieldType.FLOAT, new NumericProcessor());
    PROCESSORS.put(N2kFieldType.LOOKUP, new LookupProcessor());
    PROCESSORS.put(N2kFieldType.STRING_FIX, new StringProcessor());
    PROCESSORS.put(N2kFieldType.RESERVED, new ReservedProcessor());
    PROCESSORS.put(N2kFieldType.STRING_LAU, new StringLauProcessor());
  }

  @Getter
  private final N2kCompiledRegistry registry;

  public JsonObject decodeToJson(int pgn, byte[] payload) {
    N2kCompiledMessage message = registry.getRequiredMessage(pgn);
    if (message == null) {
      return null;
    }

    JsonObject decoded = new JsonObject();
    int cursor = message.getMinimumLengthBytes();
    int payloadBits = payload.length << 3;

    for (N2kCompiledField field : message.getFields()) {
      Processor processor = PROCESSORS.get(field.getFieldType());
      if (processor == null) {
        continue;
      }

      if (field.isCompileTimeFixed()) {
        int endBitExclusive = field.getBitOffset() + field.getBitLength();
        if (endBitExclusive > payloadBits) {
          break;
        }
      }

      cursor = processor.unpack(field, payload, cursor, decoded);
    }

    JsonObject envelope = new JsonObject();
    envelope.add("packet", decoded);
    envelope.addProperty("name", message.getId());
    return envelope;
  }

  public byte[] encodeFromJson(int pgn, JsonObject envelope) {
    N2kCompiledMessage message = registry.getRequiredMessage(pgn);
    if (message == null) {
      return new byte[0];
    }
    if (envelope == null) {
      throw new IllegalArgumentException("Envelope is null");
    }
    if (!envelope.has("packet") || envelope.get("packet").isJsonNull()) {
      throw new IllegalArgumentException("Missing 'packet' object");
    }

    JsonObject decoded = envelope.getAsJsonObject("packet");

    int payloadLengthBytes = computePayloadLengthBytes(message, decoded);
    byte[] payload = new byte[payloadLengthBytes];
    Arrays.fill(payload, (byte) 0xFF);

    int cursor = message.getMinimumLengthBytes();

    for (N2kCompiledField field : message.getFields()) {
      Processor processor = PROCESSORS.get(field.getFieldType());
      if (processor == null) {
        continue;
      }

      cursor = processor.pack(field, payload, cursor, decoded);
    }

    return payload;
  }

  public byte[] encodeFromSource(int pgn, FieldValueSource source) {
    N2kCompiledMessage message = registry.getRequiredMessage(pgn);
    if (message == null) {
      return new byte[0];
    }
    if (source == null) {
      throw new IllegalArgumentException("FieldValueSource is null");
    }

    int payloadLengthBytes = computePayloadLengthBytes(message, source);
    byte[] payload = new byte[payloadLengthBytes];
    Arrays.fill(payload, (byte) 0xFF);

    int cursor = message.getMinimumLengthBytes();

    for (N2kCompiledField field : message.getFields()) {
      Processor processor = PROCESSORS.get(field.getFieldType());
      if (processor == null) {
        continue;
      }

      cursor = processor.pack(field, payload, cursor, source);
    }

    return payload;
  }

  private int computePayloadLengthBytes(N2kCompiledMessage message, JsonObject decoded) {
    int length = message.getMinimumLengthBytes();

    for (N2kCompiledField field : message.getFields()) {
      Processor processor = PROCESSORS.get(field.getFieldType());
      if (processor == null) {
        continue;
      }

      if (field.isCompileTimeFixed()) {
        continue;
      }

      length += processor.computePayloadLength(field, decoded);
    }

    if (message.getLengthType() == N2kMessageLengthType.FIXED) {
      Integer fixedLengthBytes = message.getFixedLengthBytes();
      if (fixedLengthBytes == null) {
        throw new IllegalArgumentException(
            "FIXED lengthType but fixedLengthBytes is null for PGN " + message.getPgn()
        );
      }
      return fixedLengthBytes;
    }

    return length;
  }

  private int computePayloadLengthBytes(N2kCompiledMessage message, FieldValueSource source) {
    int length = message.getMinimumLengthBytes();

    for (N2kCompiledField field : message.getFields()) {
      Processor processor = PROCESSORS.get(field.getFieldType());
      if (processor == null) {
        continue;
      }

      if (field.isCompileTimeFixed()) {
        continue;
      }

      length += processor.computePayloadLength(field, source);
    }

    if (message.getLengthType() == N2kMessageLengthType.FIXED) {
      Integer fixedLengthBytes = message.getFixedLengthBytes();
      if (fixedLengthBytes == null) {
        throw new IllegalStateException(
            "FIXED lengthType but fixedLengthBytes is null for PGN " + message.getPgn()
        );
      }
      return fixedLengthBytes;
    }

    return length;
  }
}