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

package io.mapsmessaging.canbus.canaerospace.parser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class DataTypeCodec {

  private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

  private DataTypeCodec() {
  }

  public static Object decode(String schemaDataTypeName, byte[] dataBytes) {
    if (schemaDataTypeName == null) {
      return null;
    }
    if (dataBytes == null || dataBytes.length != 4) {
      throw new IllegalArgumentException("dataBytes must be 4 bytes");
    }

    ByteBuffer buffer = ByteBuffer.wrap(dataBytes).order(BYTE_ORDER);

    switch (schemaDataTypeName) {
      case "FLOAT" -> {
        return buffer.getFloat(0);
      }
      case "LONG" -> {
        return buffer.getInt(0);
      }
      case "ULONG" -> {
        return buffer.getInt(0) & 0xFFFFFFFFL;
      }
      case "SHORT" -> {
        return (int) buffer.getShort(2);
      }
      case "USHORT" -> {
        return buffer.getShort(2) & 0xFFFF;
      }
      case "CHAR" -> {
        return (int) dataBytes[0];
      }
      case "UCHAR" -> {
        return dataBytes[0] & 0xFF;
      }
      case "CHAR2" -> {
        int a = dataBytes[0];
        int b = dataBytes[1];
        return new int[]{a, b};
      }
      case "UCHAR2" -> {
        int a = dataBytes[0] & 0xFF;
        int b = dataBytes[1] & 0xFF;
        return new int[]{a, b};
      }
      case "CHAR4" -> {
        return new int[]{dataBytes[0], dataBytes[1], dataBytes[2], dataBytes[3]};
      }
      case "UCHAR4" -> {
        return new int[]{dataBytes[0] & 0xFF, dataBytes[1] & 0xFF, dataBytes[2] & 0xFF, dataBytes[3] & 0xFF};
      }
      case "SHORT2" -> {
        int a = buffer.getShort(0);
        int b = buffer.getShort(2);
        return new int[]{a, b};
      }
      case "USHORT2" -> {
        int a = buffer.getShort(0) & 0xFFFF;
        int b = buffer.getShort(2) & 0xFFFF;
        return new int[]{a, b};
      }
      case "BSHORT" -> {
        return buffer.getShort(0) & 0xFFFF;
      }
      case "BLONG", "MEMID", "CHKSUM" -> {
        return buffer.getInt(0) & 0xFFFFFFFFL;
      }
      case "ACHAR" -> {
        return dataBytes[0] & 0xFF;
      }
      case "ACHAR2" -> {
        return new int[]{dataBytes[0] & 0xFF, dataBytes[1] & 0xFF};
      }
      case "ACHAR4" -> {
        return new String(new byte[]{dataBytes[0], dataBytes[1], dataBytes[2], dataBytes[3]});
      }
      case "VARIABLE3" -> {
        return decodeSigned24(dataBytes);
      }
      case "UVARIABLE3" -> {
        return decodeUnsigned24(dataBytes);
      }
      case "NODATA" -> {
        return null;
      }
    }

    return dataBytes;
  }

  public static byte[] encode(String schemaDataTypeName, Object value) {
    if (schemaDataTypeName == null) {
      throw new IllegalArgumentException("schemaDataTypeName must not be null");
    }

    ByteBuffer buffer = ByteBuffer.allocate(4).order(BYTE_ORDER);

    switch (schemaDataTypeName) {
      case "FLOAT" -> {
        buffer.putFloat(0, toFloat(value));
        return buffer.array();
      }
      case "LONG" -> {
        buffer.putInt(0, toInt(value));
        return buffer.array();
      }
      case "ULONG" -> {
        long longValue = toLong(value);
        if (longValue < 0 || longValue > 0xFFFFFFFFL) {
          throw new IllegalArgumentException("ULONG out of range: " + longValue);
        }
        buffer.putInt(0, (int) longValue);
        return buffer.array();
      }
      case "SHORT" -> {
        int intValue = toInt(value);
        if (intValue < Short.MIN_VALUE || intValue > Short.MAX_VALUE) {
          throw new IllegalArgumentException("SHORT out of range: " + intValue);
        }
        buffer.putShort(2, (short) intValue);
        return buffer.array();
      }
      case "USHORT" -> {
        int intValue = toInt(value);
        if (intValue < 0 || intValue > 0xFFFF) {
          throw new IllegalArgumentException("USHORT out of range: " + intValue);
        }
        buffer.putShort(2, (short) intValue);
        return buffer.array();
      }
      case "CHAR" -> {
        int intValue = toInt(value);
        if (intValue < -128 || intValue > 127) {
          throw new IllegalArgumentException("CHAR out of range: " + intValue);
        }
        buffer.put(0, (byte) intValue);
        return buffer.array();
      }
      case "UCHAR", "ACHAR" -> {
        int intValue = toInt(value);
        if (intValue < 0 || intValue > 0xFF) {
          throw new IllegalArgumentException(schemaDataTypeName + " out of range: " + intValue);
        }
        buffer.put(0, (byte) intValue);
        return buffer.array();
      }
      case "CHAR2" -> {
        int[] values = toIntArray(value, 2);
        validateRange(values[0], -128, 127, "CHAR2[0]");
        validateRange(values[1], -128, 127, "CHAR2[1]");
        buffer.put(0, (byte) values[0]);
        buffer.put(1, (byte) values[1]);
        return buffer.array();
      }
      case "UCHAR2", "ACHAR2" -> {
        int[] values = toIntArray(value, 2);
        validateRange(values[0], 0, 0xFF, schemaDataTypeName + "[0]");
        validateRange(values[1], 0, 0xFF, schemaDataTypeName + "[1]");
        buffer.put(0, (byte) values[0]);
        buffer.put(1, (byte) values[1]);
        return buffer.array();
      }
      case "CHAR4" -> {
        int[] values = toIntArray(value, 4);
        validateRange(values[0], -128, 127, "CHAR4[0]");
        validateRange(values[1], -128, 127, "CHAR4[1]");
        validateRange(values[2], -128, 127, "CHAR4[2]");
        validateRange(values[3], -128, 127, "CHAR4[3]");
        buffer.put(0, (byte) values[0]);
        buffer.put(1, (byte) values[1]);
        buffer.put(2, (byte) values[2]);
        buffer.put(3, (byte) values[3]);
        return buffer.array();
      }
      case "UCHAR4" -> {
        int[] values = toIntArray(value, 4);
        validateRange(values[0], 0, 0xFF, "UCHAR4[0]");
        validateRange(values[1], 0, 0xFF, "UCHAR4[1]");
        validateRange(values[2], 0, 0xFF, "UCHAR4[2]");
        validateRange(values[3], 0, 0xFF, "UCHAR4[3]");
        buffer.put(0, (byte) values[0]);
        buffer.put(1, (byte) values[1]);
        buffer.put(2, (byte) values[2]);
        buffer.put(3, (byte) values[3]);
        return buffer.array();
      }
      case "SHORT2" -> {
        int[] values = toIntArray(value, 2);
        validateRange(values[0], Short.MIN_VALUE, Short.MAX_VALUE, "SHORT2[0]");
        validateRange(values[1], Short.MIN_VALUE, Short.MAX_VALUE, "SHORT2[1]");
        buffer.putShort(0, (short) values[0]);
        buffer.putShort(2, (short) values[1]);
        return buffer.array();
      }
      case "USHORT2" -> {
        int[] values = toIntArray(value, 2);
        validateRange(values[0], 0, 0xFFFF, "USHORT2[0]");
        validateRange(values[1], 0, 0xFFFF, "USHORT2[1]");
        buffer.putShort(0, (short) values[0]);
        buffer.putShort(2, (short) values[1]);
        return buffer.array();
      }
      case "BSHORT" -> {
        int intValue = toInt(value);
        if (intValue < 0 || intValue > 0xFFFF) {
          throw new IllegalArgumentException("BSHORT out of range: " + intValue);
        }
        buffer.putShort(0, (short) intValue);
        return buffer.array();
      }
      case "BLONG", "MEMID", "CHKSUM" -> {
        long longValue = toLong(value);
        if (longValue < 0 || longValue > 0xFFFFFFFFL) {
          throw new IllegalArgumentException(schemaDataTypeName + " out of range: " + longValue);
        }
        buffer.putInt(0, (int) longValue);
        return buffer.array();
      }
      case "ACHAR4" -> {
        byte[] bytes = toAchar4(value);
        buffer.put(bytes, 0, 4);
        return buffer.array();
      }
      case "VARIABLE3" -> {
        int intValue = toInt(value);
        return encodeSigned24(intValue);
      }
      case "UVARIABLE3" -> {
        int intValue = toInt(value);
        return encodeUnsigned24(intValue);
      }
      case "NODATA" -> {
        return new byte[4];
      }
      default -> {
        if (value instanceof byte[] bytes) {
          if (bytes.length != 4) {
            throw new IllegalArgumentException("byte[] value must be 4 bytes for type " + schemaDataTypeName);
          }
          return bytes.clone();
        }
        throw new IllegalArgumentException("Unsupported schemaDataTypeName: " + schemaDataTypeName);
      }
    }
  }

  private static int decodeUnsigned24(byte[] dataBytes) {
    int b0 = dataBytes[1] & 0xFF;
    int b1 = dataBytes[2] & 0xFF;
    int b2 = dataBytes[3] & 0xFF;
    return (b0 << 16) | (b1 << 8) | b2;
  }

  private static int decodeSigned24(byte[] dataBytes) {
    int value = decodeUnsigned24(dataBytes);
    if ((value & 0x800000) != 0) {
      value = value | 0xFF000000;
    }
    return value;
  }

  private static byte[] encodeUnsigned24(int value) {
    if (value < 0 || value > 0xFFFFFF) {
      throw new IllegalArgumentException("UVARIABLE3 out of range: " + value);
    }
    return new byte[]{
        0,
        (byte) ((value >> 16) & 0xFF),
        (byte) ((value >> 8) & 0xFF),
        (byte) (value & 0xFF)
    };
  }

  private static byte[] encodeSigned24(int value) {
    if (value < -8388608 || value > 8388607) {
      throw new IllegalArgumentException("VARIABLE3 out of range: " + value);
    }
    int encoded = value & 0xFFFFFF;
    return new byte[]{
        0,
        (byte) ((encoded >> 16) & 0xFF),
        (byte) ((encoded >> 8) & 0xFF),
        (byte) (encoded & 0xFF)
    };
  }

  private static int toInt(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String stringValue) {
      return Integer.parseInt(stringValue.trim());
    }
    throw new IllegalArgumentException("Unable to convert value to int: " + value);
  }

  private static long toLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String stringValue) {
      return Long.parseLong(stringValue.trim());
    }
    throw new IllegalArgumentException("Unable to convert value to long: " + value);
  }

  private static float toFloat(Object value) {
    if (value instanceof Number number) {
      return number.floatValue();
    }
    if (value instanceof String stringValue) {
      return Float.parseFloat(stringValue.trim());
    }
    throw new IllegalArgumentException("Unable to convert value to float: " + value);
  }

  private static int[] toIntArray(Object value, int expectedLength) {
    if (value instanceof int[] intArray) {
      if (intArray.length != expectedLength) {
        throw new IllegalArgumentException("Expected int[] length " + expectedLength + " but was " + intArray.length);
      }
      return intArray.clone();
    }
    if (value instanceof Integer[] integerArray) {
      if (integerArray.length != expectedLength) {
        throw new IllegalArgumentException("Expected Integer[] length " + expectedLength + " but was " + integerArray.length);
      }
      int[] result = new int[expectedLength];
      for (int index = 0; index < expectedLength; index++) {
        result[index] = integerArray[index];
      }
      return result;
    }
    if (value instanceof java.util.List<?> list) {
      if (list.size() != expectedLength) {
        throw new IllegalArgumentException("Expected list length " + expectedLength + " but was " + list.size());
      }
      int[] result = new int[expectedLength];
      for (int index = 0; index < expectedLength; index++) {
        Object entry = list.get(index);
        if (!(entry instanceof Number number)) {
          throw new IllegalArgumentException("List entry at index " + index + " is not numeric: " + entry);
        }
        result[index] = number.intValue();
      }
      return result;
    }
    throw new IllegalArgumentException("Unable to convert value to int[" + expectedLength + "]: " + value);
  }

  private static byte[] toAchar4(Object value) {
    if (value instanceof String stringValue) {
      byte[] source = stringValue.getBytes(StandardCharsets.ISO_8859_1);
      byte[] target = new byte[4];
      int length = Math.min(source.length, 4);
      System.arraycopy(source, 0, target, 0, length);
      return target;
    }
    if (value instanceof byte[] bytes) {
      if (bytes.length != 4) {
        throw new IllegalArgumentException("ACHAR4 byte[] must be length 4 but was " + bytes.length);
      }
      return bytes.clone();
    }
    if (value instanceof int[] intArray) {
      if (intArray.length != 4) {
        throw new IllegalArgumentException("ACHAR4 int[] must be length 4 but was " + intArray.length);
      }
      byte[] target = new byte[4];
      for (int index = 0; index < 4; index++) {
        validateRange(intArray[index], 0, 0xFF, "ACHAR4[" + index + "]");
        target[index] = (byte) intArray[index];
      }
      return target;
    }
    throw new IllegalArgumentException("Unable to convert value to ACHAR4: " + value);
  }

  private static void validateRange(int value, int minimum, int maximum, String fieldName) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(fieldName + " out of range: " + value);
    }
  }
}