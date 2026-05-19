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

package io.mapsmessaging.canbus.j1939.n2k.codec;

import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledField;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class StringProcessor implements Processor {

  @Override
  public int pack(N2kCompiledField field, byte[] payload, int cursor, JsonObject decoded) {
    return writeString(field, payload, cursor, decoded.has(field.getId()) && !decoded.get(field.getId()).isJsonNull()
        ? decoded.get(field.getId()).getAsString()
        : null);
  }

  @Override
  public int pack(N2kCompiledField field, byte[] payload, int cursor, FieldValueSource source) {
    return writeString(field, payload, cursor, source.getString(field.getId()));
  }

  @Override
  public int unpack(N2kCompiledField field, byte[] payload, int cursor, JsonObject decoded) {
    Position position = resolvePosition(field, cursor);

    if (position.startBit != 0) {
      throw new UnsupportedOperationException(
          "STRING_FIX must be byte-aligned: " + field.getId() + " startBit=" + position.startBit
      );
    }

    int endExclusive = Math.min(payload.length, position.startByte + position.lengthBytes);
    int safeLength = Math.max(0, endExclusive - position.startByte);

    if (safeLength <= 0) {
      decoded.addProperty(field.getId(), "");
      return position.nextCursor;
    }

    byte[] rawBytes = Arrays.copyOfRange(payload, position.startByte, position.startByte + safeLength);
    String text = new String(rawBytes, StandardCharsets.ISO_8859_1);
    text = trimRight(text, '\0', ' ');
    decoded.addProperty(field.getId(), text);
    return position.nextCursor;
  }

  @Override
  public int computePayloadLength(N2kCompiledField field, JsonObject decoded) {
    return field.isCompileTimeFixed() ? 0 : computeRelativeBytes(field);
  }

  @Override
  public int computePayloadLength(N2kCompiledField field, FieldValueSource source) {
    return field.isCompileTimeFixed() ? 0 : computeRelativeBytes(field);
  }

  private static int writeString(N2kCompiledField field, byte[] payload, int cursor, String text) {
    Position position = resolvePosition(field, cursor);

    if (position.startBit != 0) {
      throw new UnsupportedOperationException(
          "STRING_FIX must be byte-aligned: " + field.getId() + " startBit=" + position.startBit
      );
    }

    int endExclusive = Math.min(payload.length, position.startByte + position.lengthBytes);
    int safeLength = Math.max(0, endExclusive - position.startByte);
    if (safeLength <= 0) {
      return position.nextCursor;
    }

    Arrays.fill(payload, position.startByte, position.startByte + safeLength, (byte) 0x20);

    if (text == null || text.isEmpty()) {
      return position.nextCursor;
    }

    byte[] sourceBytes = text.getBytes(StandardCharsets.ISO_8859_1);
    int copyLength = Math.min(safeLength, sourceBytes.length);
    System.arraycopy(sourceBytes, 0, payload, position.startByte, copyLength);
    return position.nextCursor;
  }

  private static Position resolvePosition(N2kCompiledField field, int cursor) {
    if (field.isCompileTimeFixed()) {
      return new Position(
          field.getStartByte(),
          field.getStartBit(),
          field.getBytesToRead(),
          cursor
      );
    }

    int lengthBytes = computeRelativeBytes(field);
    return new Position(
        cursor,
        0,
        lengthBytes,
        cursor + lengthBytes
    );
  }

  private static int computeRelativeBytes(N2kCompiledField field) {
    int bitLength = field.getBitLength();
    if (bitLength <= 0) {
      return 0;
    }
    return (bitLength + 7) >>> 3;
  }

  private static String trimRight(String input, char... trimChars) {
    int end = input.length();
    while (end > 0) {
      char character = input.charAt(end - 1);
      boolean match = false;
      for (char trimCharacter : trimChars) {
        if (character == trimCharacter) {
          match = true;
          break;
        }
      }
      if (!match) {
        break;
      }
      end--;
    }
    return (end == input.length()) ? input : input.substring(0, end);
  }

  private record Position(
      int startByte,
      int startBit,
      int lengthBytes,
      int nextCursor
  ) {}

  public StringProcessor() {}
}