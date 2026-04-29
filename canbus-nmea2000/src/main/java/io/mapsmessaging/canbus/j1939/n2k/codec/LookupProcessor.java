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

public class LookupProcessor implements Processor {

  @Override
  public int pack(N2kCompiledField field, byte[] payload, int cursor, JsonObject decoded) {
    if (!decoded.has(field.getId()) || decoded.get(field.getId()).isJsonNull()) {
      return cursor;
    }

    long rawValue = decoded.get(field.getId()).getAsLong();
    return writeLookupValue(field, payload, cursor, rawValue);
  }

  @Override
  public int pack(N2kCompiledField field, byte[] payload, int cursor, FieldValueSource source) {
    Long rawValue = source.getLong(field.getId());
    if (rawValue == null) {
      return cursor;
    }

    return writeLookupValue(field, payload, cursor, rawValue);
  }

  @Override
  public int unpack(N2kCompiledField field, byte[] payload, int cursor, JsonObject decoded) {
    int startByte;
    int startBit;
    int bytesToRead;

    if (field.isCompileTimeFixed()) {
      startByte = field.getStartByte();
      startBit = field.getStartBit();
      bytesToRead = field.getBytesToRead();
    }
    else {
      startByte = cursor;
      startBit = 0;
      bytesToRead = computeRelativeBytes(field);
    }

    long raw =
        N2kBitCodec.extractBits(
            payload,
            startByte,
            startBit,
            bytesToRead,
            field.getMask(),
            field.isSigned(),
            field.getBitLength());

    decoded.addProperty(field.getId(), (int) (raw & field.getMask()));
    return field.isCompileTimeFixed() ? cursor : cursor + bytesToRead;
  }

  @Override
  public int computePayloadLength(N2kCompiledField field, JsonObject decoded) {
    return field.isCompileTimeFixed() ? 0 : computeRelativeBytes(field);
  }

  @Override
  public int computePayloadLength(N2kCompiledField field, FieldValueSource source) {
    return field.isCompileTimeFixed() ? 0 : computeRelativeBytes(field);
  }

  private static int writeLookupValue(N2kCompiledField field, byte[] payload, int cursor, long rawValue) {
    int bitLength = field.getBitLength();
    long max = (bitLength >= 64) ? -1L : ((1L << bitLength) - 1L);

    if (bitLength < 64 && rawValue > max) {
      rawValue = max;
    }
    if (rawValue < 0) {
      rawValue = 0;
    }

    int startByte;
    int startBit;
    int bytesToRead;

    if (field.isCompileTimeFixed()) {
      startByte = field.getStartByte();
      startBit = field.getStartBit();
      bytesToRead = field.getBytesToRead();
    }
    else {
      startByte = cursor;
      startBit = 0;
      bytesToRead = computeRelativeBytes(field);
    }

    N2kBitCodec.insertBits(
        payload,
        startByte,
        startBit,
        bytesToRead,
        field.getMask(),
        rawValue
    );

    return field.isCompileTimeFixed() ? cursor : cursor + bytesToRead;
  }

  private static int computeRelativeBytes(N2kCompiledField field) {
    int bitLength = field.getBitLength();
    return bitLength <= 0 ? 0 : (bitLength + 7) >>> 3;
  }
}