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