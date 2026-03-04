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

    if ("FLOAT".equals(schemaDataTypeName)) {
      return buffer.getFloat(0);
    }

    if ("LONG".equals(schemaDataTypeName)) {
      return buffer.getInt(0);
    }

    if ("ULONG".equals(schemaDataTypeName)) {
      return buffer.getInt(0) & 0xFFFFFFFFL;
    }

    if ("SHORT".equals(schemaDataTypeName)) {
      return (int) buffer.getShort(0);
    }

    if ("USHORT".equals(schemaDataTypeName)) {
      return buffer.getShort(0) & 0xFFFF;
    }

    if ("CHAR".equals(schemaDataTypeName)) {
      return (int) dataBytes[0];
    }

    if ("UCHAR".equals(schemaDataTypeName)) {
      return dataBytes[0] & 0xFF;
    }

    if ("CHAR2".equals(schemaDataTypeName)) {
      int a = dataBytes[0];
      int b = dataBytes[1];
      return new int[]{a, b};
    }

    if ("UCHAR2".equals(schemaDataTypeName)) {
      int a = dataBytes[0] & 0xFF;
      int b = dataBytes[1] & 0xFF;
      return new int[]{a, b};
    }

    if ("CHAR4".equals(schemaDataTypeName)) {
      return new int[]{dataBytes[0], dataBytes[1], dataBytes[2], dataBytes[3]};
    }

    if ("UCHAR4".equals(schemaDataTypeName)) {
      return new int[]{dataBytes[0] & 0xFF, dataBytes[1] & 0xFF, dataBytes[2] & 0xFF, dataBytes[3] & 0xFF};
    }

    if ("SHORT2".equals(schemaDataTypeName)) {
      int a = buffer.getShort(0);
      int b = buffer.getShort(2);
      return new int[]{a, b};
    }

    if ("USHORT2".equals(schemaDataTypeName)) {
      int a = buffer.getShort(0) & 0xFFFF;
      int b = buffer.getShort(2) & 0xFFFF;
      return new int[]{a, b};
    }

    if ("BSHORT".equals(schemaDataTypeName)) {
      return buffer.getShort(0) & 0xFFFF;
    }

    if ("BLONG".equals(schemaDataTypeName)) {
      return buffer.getInt(0) & 0xFFFFFFFFL;
    }

    if ("MEMID".equals(schemaDataTypeName) || "CHKSUM".equals(schemaDataTypeName)) {
      return buffer.getInt(0) & 0xFFFFFFFFL;
    }

    if ("ACHAR".equals(schemaDataTypeName)) {
      return dataBytes[0] & 0xFF;
    }

    if ("ACHAR2".equals(schemaDataTypeName)) {
      return new int[]{dataBytes[0] & 0xFF, dataBytes[1] & 0xFF};
    }

    if ("ACHAR4".equals(schemaDataTypeName)) {
      return new String(new byte[]{dataBytes[0], dataBytes[1], dataBytes[2], dataBytes[3]});
    }

    if ("VARIABLE3".equals(schemaDataTypeName)) {
      return decodeSigned24(dataBytes);
    }

    if ("UVARIABLE3".equals(schemaDataTypeName)) {
      return decodeUnsigned24(dataBytes);
    }

    if ("NODATA".equals(schemaDataTypeName)) {
      return null;
    }

    return dataBytes;
  }

  private static int decodeUnsigned24(byte[] dataBytes) {
    int b0 = dataBytes[0] & 0xFF;
    int b1 = dataBytes[1] & 0xFF;
    int b2 = dataBytes[2] & 0xFF;
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