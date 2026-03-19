package io.mapsmessaging.canbus.canaerospace.parser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
}