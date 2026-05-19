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

public class NumericProcessor implements Processor {

  @Override
  public int pack(N2kCompiledField field, byte[] payload, int cursor, JsonObject decoded) {
    if (!decoded.has(field.getId()) || decoded.get(field.getId()).isJsonNull()) {
      return cursor;
    }

    double numericValue = decoded.get(field.getId()).getAsDouble();
    return writeNumericValue(field, payload, cursor, numericValue);
  }

  @Override
  public int pack(N2kCompiledField field, byte[] payload, int cursor, FieldValueSource source) {
    Double numericValue = source.getDouble(field.getId());
    if (numericValue != null) {
      return writeNumericValue(field, payload, cursor, numericValue);
    }

    Long longValue = source.getLong(field.getId());
    if (longValue != null) {
      return writeNumericValue(field, payload, cursor, longValue.doubleValue());
    }

    return cursor;
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

    double value = raw * field.getResolution() + field.getOffset();
    decoded.addProperty(field.getId(), value);

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

  private static int writeNumericValue(N2kCompiledField field, byte[] payload, int cursor, double numericValue) {
    double resolution = field.getResolution();
    if (resolution == 0.0) {
      throw new IllegalStateException("Resolution is zero for numeric field " + field.getId());
    }

    double offset = field.getOffset();
    double unscaled = (numericValue - offset) / resolution;
    long rawValue = Math.round(unscaled);

    if (rawValue < field.getRawMin()) {
      rawValue = field.getRawMin();
    }
    else if (rawValue > field.getRawMax()) {
      rawValue = field.getRawMax();
    }

    validateRawValue(field, rawValue);

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
    if (bitLength <= 0) {
      return 0;
    }
    return (bitLength + 7) >>> 3;
  }

  private static void validateRawValue(N2kCompiledField field, long rawValue) {
    if (!field.isSigned()) {
      if (rawValue < 0) {
        throw new IllegalArgumentException("Unsigned field " + field.getId() + " cannot be negative");
      }

      long max = field.getMask();
      if (rawValue > max) {
        throw new IllegalArgumentException(
            "Field " + field.getId() + " out of range: " + rawValue + " max=" + max
        );
      }
    }
    else {
      int bitLength = field.getBitLength();
      if (bitLength > 0 && bitLength < 64) {
        long min = -(1L << (bitLength - 1));
        long max = (1L << (bitLength - 1)) - 1L;
        if (rawValue < min || rawValue > max) {
          throw new IllegalArgumentException(
              "Signed field " + field.getId() + " out of range: " + rawValue + " allowed=" + min + ".." + max
          );
        }
      }
    }
  }

  public NumericProcessor() {}
}