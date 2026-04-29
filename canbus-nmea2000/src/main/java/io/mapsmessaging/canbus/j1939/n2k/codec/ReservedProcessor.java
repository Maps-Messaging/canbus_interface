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

public class ReservedProcessor implements Processor {

  @Override
  public int pack(N2kCompiledField field, byte[] payload, int cursor, JsonObject decoded) {
    return writeReserved(field, payload, cursor);
  }

  @Override
  public int pack(N2kCompiledField field, byte[] payload, int cursor, FieldValueSource source) {
    return writeReserved(field, payload, cursor);
  }

  @Override
  public int unpack(N2kCompiledField field, byte[] payload, int cursor, JsonObject decoded) {
    return field.isCompileTimeFixed() ? cursor : cursor + computeRelativeBytes(field);
  }

  @Override
  public int computePayloadLength(N2kCompiledField field, JsonObject decoded) {
    return field.isCompileTimeFixed() ? 0 : computeRelativeBytes(field);
  }

  @Override
  public int computePayloadLength(N2kCompiledField field, FieldValueSource source) {
    return field.isCompileTimeFixed() ? 0 : computeRelativeBytes(field);
  }

  private static int writeReserved(N2kCompiledField field, byte[] payload, int cursor) {
    int bitLength = field.getBitLength();
    if (bitLength <= 0) {
      return cursor;
    }

    int startByte;
    int startBit;
    int nextCursor;

    if (field.isCompileTimeFixed()) {
      startByte = field.getStartByte();
      startBit = field.getStartBit();
      nextCursor = cursor;
    }
    else {
      startByte = cursor;
      startBit = 0;
      nextCursor = cursor + computeRelativeBytes(field);
    }

    if (startBit == 0 && (bitLength & 7) == 0) {
      int lengthBytes = bitLength >>> 3;
      int endExclusive = Math.min(payload.length, startByte + lengthBytes);

      for (int index = startByte; index < endExclusive; index++) {
        payload[index] = (byte) 0xFF;
      }
      return nextCursor;
    }

    int bitsRemaining = bitLength;
    int bitOffset = (startByte << 3) + startBit;

    while (bitsRemaining > 0) {
      int chunkBits = Math.min(bitsRemaining, 63);
      long mask = (1L << chunkBits) - 1L;

      int chunkStartByte = bitOffset >>> 3;
      int chunkStartBit = bitOffset & 7;
      int totalBits = chunkStartBit + chunkBits;
      int bytesToRead = (totalBits + 7) >>> 3;

      N2kBitCodec.insertBits(
          payload,
          chunkStartByte,
          chunkStartBit,
          bytesToRead,
          mask,
          -1L
      );

      bitOffset += chunkBits;
      bitsRemaining -= chunkBits;
    }

    return nextCursor;
  }

  private static int computeRelativeBytes(N2kCompiledField field) {
    int bitLength = field.getBitLength();
    return bitLength <= 0 ? 0 : (bitLength + 7) >>> 3;
  }
}