package io.mapsmessaging.canbus.j1939.n2k.codec;

import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.j1939.n2k.compile.N2kCompiledField;

import java.nio.charset.StandardCharsets;

public class StringLauProcessor implements Processor {

  @Override
  public int computePayloadLength(N2kCompiledField field, JsonObject decoded) {
    if (!decoded.has(field.getId()) || decoded.get(field.getId()).isJsonNull()) {
      return 0;
    }

    String value = decoded.get(field.getId()).getAsString();
    if (value == null || value.isEmpty()) {
      return 0;
    }

    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return 2 + bytes.length;
  }

  @Override
  public int computePayloadLength(N2kCompiledField field, FieldValueSource source) {
    String value = source.getString(field.getId());
    if (value == null || value.isEmpty()) {
      return 0;
    }

    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return 2 + bytes.length;
  }

  @Override
  public int pack(N2kCompiledField field, byte[] payload, int cursor, JsonObject decoded) {
    if (!decoded.has(field.getId()) || decoded.get(field.getId()).isJsonNull()) {
      return cursor;
    }

    String value = decoded.get(field.getId()).getAsString();
    if (value == null || value.isEmpty()) {
      return cursor;
    }

    return writeString(payload, cursor, value);
  }

  @Override
  public int pack(N2kCompiledField field, byte[] payload, int cursor, FieldValueSource source) {
    String value = source.getString(field.getId());
    if (value == null || value.isEmpty()) {
      return cursor;
    }

    return writeString(payload, cursor, value);
  }

  @Override
  public int unpack(N2kCompiledField field, byte[] payload, int cursor, JsonObject decoded) {
    if (cursor >= payload.length) {
      return cursor;
    }

    int length = payload[cursor++] & 0xFF;
    if (length == 0 || cursor >= payload.length) {
      return cursor;
    }

    int control = payload[cursor++] & 0xFF;
    int stringLength = Math.max(0, length - 1);

    if (cursor + stringLength > payload.length) {
      stringLength = payload.length - cursor;
    }

    String value;
    if (control == 0x01) {
      value = new String(payload, cursor, stringLength, StandardCharsets.UTF_8);
    }
    else {
      value = new String(payload, cursor, stringLength, StandardCharsets.ISO_8859_1);
    }

    decoded.addProperty(field.getId(), value);
    return cursor + stringLength;
  }

  private int writeString(byte[] payload, int cursor, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

    payload[cursor++] = (byte) (bytes.length + 1);
    payload[cursor++] = 0x01;
    System.arraycopy(bytes, 0, payload, cursor, bytes.length);
    return cursor + bytes.length;
  }
}