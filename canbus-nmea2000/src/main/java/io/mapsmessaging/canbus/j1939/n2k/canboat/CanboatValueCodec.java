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

package io.mapsmessaging.canbus.j1939.n2k.canboat;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Scalar, decimal and text representations of CANboat wire values. */
final class CanboatValueCodec {
  private CanboatValueCodec() {}

  static JsonElement scalar(CanboatField field, BigInteger raw, int bits) {
    if (field.type().equals("RESERVED") || field.type().equals("SPARE")) {
      return JsonNull.INSTANCE;
    }
    for (String key : List.of("UnknownValue", "OutOfRangeValue", "ReservedValue")) {
      if (raw.equals(field.integer(key))) {
        return JsonNull.INSTANCE;
      }
    }
    if (field.type().equals("FLOAT")) {
      double value = bits == 32 ? Float.intBitsToFloat(raw.intValue()) : Double.longBitsToDouble(raw.longValue());
      return Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    }
    BigInteger value = raw;
    if (Boolean.parseBoolean(field.text("Signed")) && raw.testBit(bits - 1)) {
      value = raw.subtract(BigInteger.ONE.shiftLeft(bits));
    }
    BigDecimal scaled = new BigDecimal(value).multiply(decimal(field, "Resolution", "1")).add(decimal(field, "Offset", "0"));
    if (field.type().equals("MMSI")) {
      return new JsonPrimitive(String.format(Locale.ROOT, "%09d", value));
    }
    return new JsonPrimitive(scaled.stripTrailingZeros());
  }

  static BigInteger numericRaw(CanboatField field, JsonElement input) {
    BigDecimal resolution = decimal(field, "Resolution", "1");
    if (resolution.signum() == 0) {
      throw new IllegalArgumentException("Zero field resolution");
    }
    return input.getAsBigDecimal().subtract(decimal(field, "Offset", "0")).divide(resolution, 0, RoundingMode.HALF_UP).toBigIntegerExact();
  }

  static BigDecimal decimal(CanboatField field, String key, String fallback) {
    return new BigDecimal(field.text(key, fallback));
  }

  static JsonElement decimal(CanboatField field, JsonElement input, CanboatBits wire, int bits, boolean encode, Map<Integer, BigInteger> rawValues) {
    int count = bits / 8;
    if (bits % 8 != 0) {
      throw new IllegalArgumentException("Invalid decimal width");
    }
    if (encode) {
      return encodeDecimal(input, wire, count);
    }
    return decodeDecimal(field, wire, count, rawValues);
  }

  private static JsonElement encodeDecimal(JsonElement input, CanboatBits wire, int count) {
    if (input == null || input.isJsonNull()) {
      for (int index = 0; index < count; index++) {
        wire.write(BigInteger.valueOf(255), 8);
      }
      return JsonNull.INSTANCE;
    }
    String value = input.getAsString();
    if (!value.matches("\\d*") || value.length() > count * 2) {
      throw new IllegalArgumentException("Invalid decimal value");
    }
    // CANboat DECIMAL pairs pad an odd digit count at the end (ITU-R M.585).
    if (value.length() % 2 != 0) {
      value += "0";
    }
    value = "0".repeat(Math.max(0, count * 2 - value.length())) + value;
    for (int index = 0; index < count; index++) {
      wire.write(BigInteger.valueOf(Integer.parseInt(value.substring(index * 2, index * 2 + 2))), 8);
    }
    return input;
  }

  private static JsonElement decodeDecimal(CanboatField field, CanboatBits wire, int count, Map<Integer, BigInteger> rawValues) {
    StringBuilder text = new StringBuilder();
    for (int index = 0; index < count; index++) {
      int pair = wire.read(8).intValue();
      if (pair == 255) {
        continue;
      }
      if (pair > 99) {
        throw new IllegalArgumentException("Invalid decimal pair");
      }
      text.append(String.format(Locale.ROOT, "%02d", pair));
    }
    if (text.isEmpty()) {
      return JsonNull.INSTANCE;
    }
    int discriminator = field.number("TrailingZeroIsDigitField", 0);
    boolean keepTrailingZero =
        discriminator != 0 && field.integer("TrailingZeroIsDigitValue") != null && field.integer("TrailingZeroIsDigitValue").equals(rawValues.get(discriminator));
    if (!keepTrailingZero && text.charAt(text.length() - 1) == '0') {
      text.setLength(text.length() - 1);
    }
    return new JsonPrimitive(text.toString());
  }

  static JsonElement string(CanboatField field, JsonElement input, CanboatBits wire, boolean encode) {
    String type = field.type();
    int length = field.number("BitLength", 0) / 8;
    if (encode) {
      return encodeString(field, input, wire, type, length);
    }
    return decodeString(field, wire, type, length);
  }

  private static JsonElement encodeString(CanboatField field, JsonElement input, CanboatBits wire, String type, int length) {
    String text = input == null || input.isJsonNull() ? "" : input.getAsString();
    byte[] bytes = CanboatText.encode(text, field.text("Encoding"));
    if (type.equals("STRING_FIX")) {
      if (bytes.length > length) {
        throw new IllegalArgumentException("String exceeds fixed field width: " + field.id());
      }
      wire.writeBytes(bytes, bytes.length * 8);
      for (int index = bytes.length; index < length; index++) {
        wire.write(BigInteger.valueOf(255), 8);
      }
    } else if (type.equals("STRING_LAU")) {
      if (bytes.length > 253) {
        throw new IllegalArgumentException("LAU string exceeds one-byte length");
      }
      wire.write(BigInteger.valueOf(bytes.length + 2L), 8);
      wire.write(BigInteger.ONE, 8);
      wire.writeBytes(bytes, bytes.length * 8);
    } else {
      if (bytes.length > 253) {
        throw new IllegalArgumentException("LZ string exceeds one-byte length");
      }
      wire.write(BigInteger.valueOf(bytes.length), 8);
      wire.writeBytes(bytes, bytes.length * 8);
      wire.write(BigInteger.ZERO, 8);
    }
    return input;
  }

  private static JsonElement decodeString(CanboatField field, CanboatBits wire, String type, int length) {
    int encoding = 1;
    if (type.equals("STRING_LAU")) {
      length = wire.read(8).intValue();
      if (length < 2) {
        throw new IllegalArgumentException("Invalid LAU string length");
      }
      encoding = wire.read(8).intValue();
      length -= 2;
    } else if (type.equals("STRING_LZ")) {
      length = wire.read(8).intValue();
    }
    byte[] bytes = wire.readBytes(length * 8);
    if (type.equals("STRING_LZ") && wire.read(8).intValue() != 0) {
      throw new IllegalArgumentException("Missing LZ zero terminator");
    }
    return decodeStringBytes(field, bytes, encoding);
  }

  private static JsonElement decodeStringBytes(CanboatField field, byte[] bytes, int encoding) {
    if (encoding == 255) {
      return new JsonPrimitive("");
    }
    if (encoding != 0 && encoding != 1) {
      throw new IllegalArgumentException("Unsupported LAU encoding: " + encoding);
    }
    if (encoding == 0 && bytes.length % 2 != 0) {
      throw new IllegalArgumentException("Invalid UTF16 byte count");
    }
    int end = bytes.length;
    if (encoding == 1) {
      while (end > 0 && (bytes[end - 1] == 0 || (bytes[end - 1] & 255) == 255)) {
        end--;
      }
    }
    String value = CanboatText.decode(Arrays.copyOf(bytes, end), encoding, field.text("Encoding"));
    return new JsonPrimitive(trimNullSuffix(value).stripTrailing());
  }

  private static String trimNullSuffix(String value) {
    int suffixEnd = value.length();
    if (suffixEnd == 0) {
      return value;
    }
    // Java regex '$' also matches immediately before one final line terminator.
    char last = value.charAt(suffixEnd - 1);
    if (last == '\n' && suffixEnd > 1 && value.charAt(suffixEnd - 2) == '\r') {
      suffixEnd -= 2;
    } else if (last == '\n' || last == '\r' || last == '\u0085' || last == '\u2028' || last == '\u2029') {
      suffixEnd--;
    }
    int start = suffixEnd;
    while (start > 0 && value.charAt(start - 1) == '\u0000') {
      start--;
    }
    return value.substring(0, start) + value.substring(suffixEnd);
  }

  static boolean proprietary(int pgn) {
    int pf = (pgn >> 8) & 255;
    return pf == 239 || pf == 255;
  }

  static int binaryLength(JsonElement value) {
    return value == null || value.isJsonNull() ? 0 : Base64.getDecoder().decode(value.getAsString()).length;
  }

  static BigInteger mask(int bits) {
    return BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
  }

  static BigInteger unsigned(long value) {
    return new BigInteger(Long.toUnsignedString(value));
  }

  static IllegalArgumentException bad(CanboatMessage message, String reason) {
    return new IllegalArgumentException("PGN " + message.pgn() + " (" + message.id() + "): " + reason);
  }
}
