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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.mapsmessaging.canbus.j1939.n2k.codec.FieldValueSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Message-payload codec for generated CANboat XML. This class has no transport state. */
public final class CanboatCodec {
  private final CanboatDatabase database;

  public CanboatCodec(CanboatDatabase database) {
    this.database = Objects.requireNonNull(database);
  }

  public JsonObject decode(int pgn, byte[] payload) {
    Objects.requireNonNull(payload, "payload");
    CanboatMessage message = select(pgn, payload, null);
    if (message == null) {
      return null;
    }
    if (payload.length < message.minimum()) {
      throw bad(message, "payload shorter than minimum " + message.minimum());
    }
    JsonObject packet = new JsonObject();
    if (message.fallback()) {
      packet.addProperty("data", Base64.getEncoder().encodeToString(payload));
    } else {
      walk(
          message,
          0,
          message.fields().size(),
          packet,
          new State(new Bits(payload), packet),
          false,
          false);
    }
    JsonObject envelope = new JsonObject();
    envelope.add("packet", packet);
    envelope.addProperty("name", message.id());
    return envelope;
  }

  public byte[] encode(int pgn, JsonObject envelope) {
    Objects.requireNonNull(envelope, "envelope");
    if (!envelope.has("packet") || !envelope.get("packet").isJsonObject()) {
      throw new IllegalArgumentException("Missing 'packet' object");
    }
    JsonObject packet = envelope.getAsJsonObject("packet").deepCopy();
    String name = envelope.has("name") ? envelope.get("name").getAsString() : null;
    CanboatMessage message = select(pgn, null, packet, name);
    if (message == null) {
      return new byte[0];
    }
    if (message.fallback()) {
      return packet.has("data")
          ? Base64.getDecoder().decode(packet.get("data").getAsString())
          : new byte[0];
    }
    for (int group = 1; group <= 2; group++) {
      String key = group == 1 ? "list" : "list2";
      int counter = message.number("RepeatingFieldSet" + group + "CountField", 0);
      if (counter > 0) {
        CanboatField field = message.field(counter);
        if (field != null) {
          packet.addProperty(field.id(), packet.has(key) ? packet.getAsJsonArray(key).size() : 0);
        }
      }
    }
    State state = new State(new Bits(), packet);
    walk(message, 0, message.fields().size(), packet, state, true, false);
    int actual = (state.bits.position + 7) / 8;
    int size = message.length() >= 0 ? message.length() : Math.max(actual, message.minimum());
    if (actual > size) {
      throw bad(message, "encoded payload exceeds declared length");
    }
    return state.bits.finish(size);
  }

  public byte[] encode(int pgn, FieldValueSource source) {
    Objects.requireNonNull(source, "source");
    List<CanboatMessage> candidates = database.candidates(pgn);
    if (candidates.isEmpty()) {
      return new byte[0];
    }
    JsonObject packet = new JsonObject();
    for (CanboatMessage message : candidates) {
      for (CanboatField field : message.fields()) {
        if (!source.has(field.id())) {
          continue;
        }
        if (field.type().startsWith("STRING")
            || field.type().equals("BINARY")
            || field.type().equals("DECIMAL")) {
          String text = source.getString(field.id());
          if (text != null) {
            packet.addProperty(field.id(), text);
          }
        } else {
          Double number = source.getDouble(field.id());
          Long integer = source.getLong(field.id());
          if (number != null) {
            packet.addProperty(field.id(), number);
          } else if (integer != null) {
            packet.addProperty(field.id(), integer);
          }
        }
      }
    }
    JsonObject envelope = new JsonObject();
    envelope.add("packet", packet);
    return encode(pgn, envelope);
  }

  private CanboatMessage select(int pgn, byte[] wire, JsonObject packet) {
    return select(pgn, wire, packet, null);
  }

  private CanboatMessage select(int pgn, byte[] wire, JsonObject packet, String name) {
    List<CanboatMessage> candidates = database.candidates(pgn);
    if (candidates.isEmpty()) {
      return null;
    }
    List<CanboatMessage> matching = new ArrayList<>();
    for (CanboatMessage message : candidates) {
      if (name != null && !name.equals(message.id())) {
        continue;
      }
      if (!message.fallback()
          && matches(
              message,
              wire,
              packet,
              wire == null
                  && name == null
                  && candidates.stream().filter(m -> !m.fallback()).count() > 1)) {
        matching.add(message);
      }
    }
    if (!matching.isEmpty()) {
      if (wire == null && matching.size() > 1) {
        throw new IllegalArgumentException(
            "Ambiguous CANboat variant for PGN " + pgn + "; supply name or discriminator fields");
      }
      return matching.get(0);
    }
    CanboatMessage range = database.fallback(pgn);
    if (range != null && ((wire != null && name == null) || range.id().equals(name))) {
      return range;
    }
    if (name != null) {
      for (CanboatMessage message : candidates) {
        if (message.fallback() && name.equals(message.id())) {
          return message;
        }
      }
    }
    if (name != null) {
      throw new IllegalArgumentException(
          "Unknown or conflicting CANboat variant '" + name + "' for PGN " + pgn);
    }
    for (CanboatMessage message : candidates) {
      if (message.fallback()) {
        return message;
      }
    }
    throw new IllegalArgumentException("No matching CANboat variant for PGN " + pgn);
  }

  private boolean matches(
      CanboatMessage message, byte[] wire, JsonObject packet, boolean requireMatchValues) {
    for (CanboatField field : message.fields()) {
      BigInteger expected = field.integer("Match");
      if (expected == null) {
        continue;
      }
      if (wire != null) {
        int start = field.number("BitOffset", -1), length = field.number("BitLength", 0);
        if (start < 0 || length < 1 || start + length > wire.length * 8) {
          return false;
        }
        Bits bits = new Bits(wire);
        bits.position = start;
        if (!expected.equals(bits.read(length))) {
          return false;
        }
      } else if (packet != null && packet.has(field.id()) && !packet.get(field.id()).isJsonNull()) {
        if (!expected.equals(numericRaw(field, packet.get(field.id())))) {
          return false;
        }
      } else if (requireMatchValues) {
        return false;
      }
    }
    return true;
  }

  private void walk(
      CanboatMessage message,
      int from,
      int to,
      JsonObject packet,
      State state,
      boolean encode,
      boolean repeated) {
    List<CanboatField> fields = message.fields();
    for (int index = from; index < to; index++) {
      CanboatField field = fields.get(index);
      int group = 0;
      if (!repeated) {
        for (int candidate = 1; candidate <= 2; candidate++) {
          if (message.number("RepeatingFieldSet" + candidate + "StartField", -1) == field.order()) {
            group = candidate;
            break;
          }
        }
      }
      if (group > 0) {
        int count = message.number("RepeatingFieldSet" + group + "Size", 0);
        if (count < 1 || index + count > fields.size()) {
          throw bad(message, "invalid repeating-field descriptor");
        }
        int counter = message.number("RepeatingFieldSet" + group + "CountField", 0);
        String key = group == 1 ? "list" : "list2";
        JsonArray rows =
            encode
                ? packet.has(key) ? packet.getAsJsonArray(key) : new JsonArray()
                : new JsonArray();
        int repetitions;
        if (encode) {
          repetitions = rows.size();
        } else if (counter > 0) {
          BigInteger raw = state.raw.get(counter);
          if (raw == null || raw.signum() < 0 || raw.compareTo(BigInteger.valueOf(65535)) > 0) {
            throw bad(message, "invalid repeating-field count");
          }
          repetitions = raw.intValue();
        } else {
          repetitions = -1;
        }
        for (int row = 0;
            repetitions < 0
                ? state.bits.remaining() >= minimumRepeatBits(fields, index, count)
                : row < repetitions;
            row++) {
          if (row >= 65535) {
            throw bad(message, "too many repeated records");
          }
          JsonObject entry = encode ? rows.get(row).getAsJsonObject() : new JsonObject();
          State child = state.child();
          int before = state.bits.position;
          walk(message, index, index + count, entry, child, encode, true);
          if (state.bits.position <= before) {
            throw bad(message, "repeated record made no progress");
          }
          if (!encode) {
            rows.add(entry);
          }
        }
        if (!encode && repetitions < 0 && state.bits.remaining() > 0 && !state.bits.paddingOnly()) {
          throw bad(message, "truncated repeating-field record");
        }
        if (!encode) {
          packet.add(key, rows);
        }
        index += count - 1;
        continue;
      }
      if ("PGNIsProprietary".equals(field.text("Condition"))) {
        if (state.referencePgn == null || !proprietary(state.referencePgn)) {
          continue;
        }
      } else if (field.text("Condition") != null) {
        throw bad(message, "unsupported field condition " + field.text("Condition"));
      }
      if (!encode && state.bits.remaining() == 0 && !repeated) {
        break;
      }
      value(message, field, packet, state, encode, repeated);
    }
  }

  private static int minimumRepeatBits(List<CanboatField> fields, int start, int count) {
    int bits = 0;
    for (int index = start; index < start + count; index++) {
      bits += Math.max(0, fields.get(index).number("BitLength", 0));
    }
    return Math.max(8, bits);
  }

  private void value(
      CanboatMessage message,
      CanboatField original,
      JsonObject packet,
      State state,
      boolean encode,
      boolean repeated) {
    CanboatField field = original;
    int bits = field.number("BitLength", 0);
    boolean variableReference = field.type().equals("VARIABLE");
    int referencePadding = 0;
    boolean dynamic = field.type().equals("DYNAMIC_FIELD_VALUE");
    if (variableReference) {
      field = referenced(original, state);
      bits = field.number("BitLength", 0);
      // Group-function parameter values occupy complete bytes even for bit fields.
      referencePadding = ((bits + 7) / 8) * 8 - bits;
    }
    if (dynamic) {
      Map<String, String> metadata = new LinkedHashMap<>(original.properties());
      if (state.dynamic != null) {
        metadata.putAll(state.dynamic);
        if (state.dynamic.containsKey("Bits")) {
          metadata.put("BitLength", state.dynamic.get("Bits"));
        }
      } else {
        metadata.put("FieldType", "BINARY");
      }
      metadata.put("Id", original.id());
      metadata.remove("Match");
      field = new CanboatField(metadata);
      bits =
          state.dynamicLength == null
              ? field.number("BitLength", 0)
              : Math.max(0, state.dynamicLength - original.number("DynamicFieldLengthOverhead", 0))
                  * 8;
      if (bits == 0 && state.dynamicLength == null) {
        bits = encode ? binaryLength(packet.get(original.id())) * 8 : state.bits.remaining();
      }
    }
    if (dynamic
        && bits >= 8
        && java.util.Set.of("NUMBER", "DURATION", "TIME", "DATE", "ADDRESS", "PGN", "FIELD_INDEX")
            .contains(field.type())) {
      Map<String, String> metadata = new LinkedHashMap<>(field.properties());
      BigInteger maximum = Boolean.parseBoolean(field.text("Signed")) ? mask(bits - 1) : mask(bits);
      metadata.putIfAbsent("UnknownValue", maximum.toString());
      metadata.putIfAbsent("OutOfRangeValue", maximum.subtract(BigInteger.ONE).toString());
      metadata.putIfAbsent("ReservedValue", maximum.subtract(BigInteger.TWO).toString());
      field = new CanboatField(metadata);
    }
    if (field.text("BitLengthField") != null) {
      BigInteger length = state.raw.get(field.number("BitLengthField", 0));
      if (length == null
          || length.signum() < 0
          || length.compareTo(BigInteger.valueOf(8_000_000)) > 0) {
        throw bad(message, "invalid referenced bit length");
      }
      bits = length.intValue();
    }
    String type = field.type();
    JsonElement input = packet.get(original.id());
    String continues = original.text("Continues");
    CanboatField continued =
        continues == null
            ? null
            : message.fields().stream()
                .filter(f -> f.id().equals(continues))
                .findFirst()
                .orElse(null);
    if (encode
        && continued != null
        && packet.has(continues)
        && !packet.get(continues).isJsonNull()) {
      BigInteger combined = packet.get(continues).getAsBigInteger();
      input = new JsonPrimitive(combined.shiftRight(continued.number("BitLength", 0)));
    }
    JsonElement output;
    BigInteger raw = null;
    if (dynamic && bits == 0) {
      output = JsonNull.INSTANCE;
    } else if (type.startsWith("STRING")) {
      output = string(field, input, state.bits, encode);
    } else if (type.equals("BINARY") || bits > 64 && !type.equals("DECIMAL")) {
      if (bits == 0 && field.text("BitLength") == null) {
        bits = encode ? binaryLength(input) * 8 : state.bits.remaining();
      }
      if (dynamic && !type.equals("BINARY") && bits > 64) {
        if (encode) {
          throw bad(message, "cannot encode overwide dynamic numeric value");
        }
        state.bits.read(bits);
        output = JsonNull.INSTANCE;
      } else {
        if (encode) {
          byte[] bytes =
              input == null || input.isJsonNull()
                  ? new byte[(bits + 7) / 8]
                  : Base64.getDecoder().decode(input.getAsString());
          if (bytes.length * 8 < bits) {
            throw bad(message, "binary value is shorter than declared length");
          }
          state.bits.writeBytes(bytes, bits);
          output = input;
        } else {
          output =
              new JsonPrimitive(Base64.getEncoder().encodeToString(state.bits.readBytes(bits)));
        }
      }
    } else if (type.equals("DECIMAL")) {
      output = decimal(field, input, state.bits, bits, encode);
    } else {
      if (bits <= 0 || bits > 64) {
        throw bad(message, "invalid width for " + original.id());
      }
      if (!encode
          && state.bits.remaining() < bits
          && !repeated
          && state.bits.bytesLength() >= message.minimum()) {
        return;
      }
      if (encode) {
        BigInteger match = original.integer("Match");
        if (match != null) {
          raw = match;
          if (input != null && !input.isJsonNull() && !raw.equals(numericRaw(field, input))) {
            throw bad(message, "conflicting discriminator " + original.id());
          }
        } else if (type.equals("RESERVED") || type.equals("SPARE")) {
          raw = mask(bits);
        } else if (original.type().equals("DYNAMIC_FIELD_LENGTH")
            && (input == null || input.isJsonNull())) {
          raw = BigInteger.ZERO;
        } else if (input == null || input.isJsonNull()) {
          raw = field.integer("UnknownValue");
          if (raw == null) {
            raw = Boolean.parseBoolean(field.text("Signed")) ? mask(bits - 1) : mask(bits);
          }
        } else if (type.equals("FLOAT")) {
          raw =
              bits == 32
                  ? BigInteger.valueOf(
                      Integer.toUnsignedLong(Float.floatToIntBits(input.getAsFloat())))
                  : unsigned(Double.doubleToLongBits(input.getAsDouble()));
        } else {
          raw = numericRaw(field, input);
          int totalBits = bits;
          for (CanboatField fragment : message.fields()) {
            if (original.id().equals(fragment.text("Continues"))) {
              totalBits += fragment.number("BitLength", 0);
            }
          }
          BigInteger minimum =
              Boolean.parseBoolean(field.text("Signed"))
                  ? BigInteger.ONE.shiftLeft(totalBits - 1).negate()
                  : BigInteger.ZERO;
          BigInteger maximum =
              Boolean.parseBoolean(field.text("Signed")) ? mask(totalBits - 1) : mask(totalBits);
          if (raw.compareTo(minimum) < 0 || raw.compareTo(maximum) > 0) {
            throw bad(message, "value outside wire range for " + original.id());
          }
          if (totalBits > bits) {
            raw = raw.and(mask(bits));
          }
        }
        if (raw.signum() < 0) {
          raw = raw.add(BigInteger.ONE.shiftLeft(bits));
        }
        if (raw.signum() < 0 || raw.bitLength() > bits) {
          throw bad(message, "value outside wire range for " + original.id());
        }
        state.bits.write(raw, bits);
        output = input;
      } else {
        raw = state.bits.read(bits);
        output = scalar(field, raw, bits);
      }
    }
    if (!encode && continued != null && raw != null) {
      BigInteger low = state.raw.get(continued.order());
      if (low == null) {
        throw bad(message, "continuation has no preceding field");
      }
      BigInteger combined = low.or(raw.shiftLeft(continued.number("BitLength", 0)));
      packet.addProperty(continues, combined);
    } else if (!encode && !type.equals("RESERVED") && !type.equals("SPARE")) {
      packet.add(original.id(), output == null ? JsonNull.INSTANCE : output);
    }
    if (raw != null) {
      state.raw.put(original.order(), raw);
      switch (original.type()) {
        case "PGN" -> state.referencePgn = raw.intValue();
        case "FIELD_INDEX" -> state.fieldIndex = raw.intValue();
        case "DYNAMIC_FIELD_KEY" ->
            state.dynamic =
                database.lookup(original.text("LookupFieldTypeEnumeration"), raw.toString());
        case "DYNAMIC_FIELD_LENGTH" -> {
          if (raw.compareTo(BigInteger.valueOf(1_000_000)) > 0) {
            throw bad(message, "dynamic length is too large");
          }
          state.dynamicLength = raw.intValue() - original.number("DynamicFieldLengthOverhead", 0);
          if (state.dynamicLength < 0) {
            throw bad(message, "dynamic length smaller than overhead");
          }
        }
        default -> {}
      }
    }
    if (referencePadding > 0) {
      if (encode) {
        state.bits.write(mask(referencePadding), referencePadding);
      } else {
        state.bits.read(referencePadding);
      }
    }
    if (variableReference && raw != null && state.fieldIndex != null) {
      state.parameters.put(state.fieldIndex, raw);
    }
    if (dynamic) {
      state.dynamic = null;
      state.dynamicLength = null;
    }
  }

  private CanboatField referenced(CanboatField original, State state) {
    if (state.referencePgn == null || state.fieldIndex == null) {
      throw new IllegalArgumentException("Variable field lacks referenced PGN/field index");
    }
    for (CanboatMessage candidate : database.candidates(state.referencePgn)) {
      if (candidate.fallback()) {
        continue;
      }
      boolean matches = true;
      for (CanboatField field : candidate.fields()) {
        if (field.integer("Match") != null
            && state.parameters.containsKey(field.order())
            && !state.parameters.get(field.order()).equals(field.integer("Match"))) {
          matches = false;
          break;
        }
      }
      CanboatField target = candidate.field(state.fieldIndex);
      if (matches && target != null) {
        Map<String, String> properties = new LinkedHashMap<>(target.properties());
        properties.put("Id", original.id());
        properties.remove("Match");
        return new CanboatField(properties);
      }
    }
    throw new IllegalArgumentException(
        "Unknown referenced field " + state.fieldIndex + " in PGN " + state.referencePgn);
  }

  private static JsonElement scalar(CanboatField field, BigInteger raw, int bits) {
    if (field.type().equals("RESERVED") || field.type().equals("SPARE")) {
      return JsonNull.INSTANCE;
    }
    for (String key : List.of("UnknownValue", "OutOfRangeValue", "ReservedValue")) {
      if (raw.equals(field.integer(key))) {
        return JsonNull.INSTANCE;
      }
    }
    if (field.type().equals("FLOAT")) {
      double value =
          bits == 32
              ? Float.intBitsToFloat(raw.intValue())
              : Double.longBitsToDouble(raw.longValue());
      return Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    }
    BigInteger value = raw;
    if (Boolean.parseBoolean(field.text("Signed")) && raw.testBit(bits - 1)) {
      value = raw.subtract(BigInteger.ONE.shiftLeft(bits));
    }
    BigDecimal scaled =
        new BigDecimal(value)
            .multiply(decimal(field, "Resolution", "1"))
            .add(decimal(field, "Offset", "0"));
    if (field.type().equals("MMSI")) {
      return new JsonPrimitive(String.format(Locale.ROOT, "%09d", value));
    }
    return new JsonPrimitive(scaled.stripTrailingZeros());
  }

  private static BigInteger numericRaw(CanboatField field, JsonElement input) {
    BigDecimal resolution = decimal(field, "Resolution", "1");
    if (resolution.signum() == 0) {
      throw new IllegalArgumentException("Zero field resolution");
    }
    return input
        .getAsBigDecimal()
        .subtract(decimal(field, "Offset", "0"))
        .divide(resolution, 0, RoundingMode.HALF_UP)
        .toBigIntegerExact();
  }

  private static BigDecimal decimal(CanboatField field, String key, String fallback) {
    return new BigDecimal(field.text(key, fallback));
  }

  private static JsonElement decimal(
      CanboatField field, JsonElement input, Bits wire, int bits, boolean encode) {
    int count = bits / 8;
    if (bits % 8 != 0) {
      throw new IllegalArgumentException("Invalid decimal width");
    }
    if (encode) {
      if (input == null || input.isJsonNull()) {
        for (int index = 0; index < count; index++) {
          wire.write(BigInteger.valueOf(255), 8);
        }
        return JsonNull.INSTANCE;
      }
      String value = input.getAsString();
      if (!value.matches("[0-9]*") || value.length() > count * 2) {
        throw new IllegalArgumentException("Invalid decimal value");
      }
      value = "0".repeat(Math.max(0, count * 2 - value.length())) + value;
      for (int index = 0; index < count; index++) {
        wire.write(
            BigInteger.valueOf(Integer.parseInt(value.substring(index * 2, index * 2 + 2))), 8);
      }
      return input;
    }
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
    return text.isEmpty() ? JsonNull.INSTANCE : new JsonPrimitive(text.toString());
  }

  private static JsonElement string(
      CanboatField field, JsonElement input, Bits wire, boolean encode) {
    String type = field.type();
    int length = field.number("BitLength", 0) / 8;
    if (encode) {
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
        wire.write(BigInteger.valueOf(bytes.length + 2), 8);
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
    return new JsonPrimitive(value.replaceAll("\u0000+$", "").stripTrailing());
  }

  private static boolean proprietary(int pgn) {
    int pf = (pgn >> 8) & 255;
    return pf == 239 || pf == 255;
  }

  private static int binaryLength(JsonElement value) {
    return value == null || value.isJsonNull()
        ? 0
        : Base64.getDecoder().decode(value.getAsString()).length;
  }

  private static BigInteger mask(int bits) {
    return BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
  }

  private static BigInteger unsigned(long value) {
    return new BigInteger(Long.toUnsignedString(value));
  }

  private static IllegalArgumentException bad(CanboatMessage message, String reason) {
    return new IllegalArgumentException(
        "PGN " + message.pgn() + " (" + message.id() + "): " + reason);
  }

  private static final class State {
    final Bits bits;
    final JsonObject root;
    final Map<Integer, BigInteger> raw;
    final Map<Integer, BigInteger> parameters;
    Integer referencePgn;
    Integer fieldIndex;
    Integer dynamicLength;
    Map<String, String> dynamic;

    State(Bits bits, JsonObject root) {
      this(bits, root, new HashMap<>(), new HashMap<>());
    }

    State(
        Bits bits,
        JsonObject root,
        Map<Integer, BigInteger> raw,
        Map<Integer, BigInteger> parameters) {
      this.bits = bits;
      this.root = root;
      this.raw = raw;
      this.parameters = parameters;
    }

    State child() {
      State child = new State(bits, root, new HashMap<>(raw), parameters);
      child.referencePgn = referencePgn;
      return child;
    }
  }

  private static final class Bits {
    byte[] data;
    final boolean input;
    int position;

    Bits(byte[] bytes) {
      data = bytes;
      input = true;
    }

    Bits() {
      data = new byte[256];
      Arrays.fill(data, (byte) 255);
      input = false;
    }

    int remaining() {
      return data.length * 8 - position;
    }

    int bytesLength() {
      return data.length;
    }

    boolean paddingOnly() {
      for (int bit = position; bit < data.length * 8; bit++) {
        if ((data[bit / 8] & (1 << (bit % 8))) == 0) {
          return false;
        }
      }
      return true;
    }

    BigInteger read(int length) {
      byte[] bytes = readBytes(length);
      byte[] reversed = new byte[bytes.length + 1];
      for (int index = 0; index < bytes.length; index++) {
        reversed[bytes.length - index] = bytes[index];
      }
      return new BigInteger(reversed);
    }

    void write(BigInteger value, int length) {
      if (length < 0 || position + (long) length > 8_000_000) {
        throw new IllegalArgumentException("CANboat payload is too large");
      }
      int required = (position + length + 7) / 8;
      if (required > data.length) {
        int previous = data.length;
        data = Arrays.copyOf(data, Math.max(required, previous * 2));
        Arrays.fill(data, previous, data.length, (byte) 255);
      }
      for (int index = 0; index < length; index++) {
        int byteIndex = (position + index) / 8, bit = 1 << ((position + index) % 8);
        if (value.testBit(index)) {
          data[byteIndex] |= (byte) bit;
        } else {
          data[byteIndex] &= (byte) ~bit;
        }
      }
      position += length;
    }

    byte[] readBytes(int length) {
      if (length < 0 || length > remaining()) {
        throw new IllegalArgumentException(
            "Truncated CANboat field at bit " + position + ", needs " + length);
      }
      byte[] bytes = new byte[(length + 7) / 8];
      for (int index = 0; index < length; index++) {
        if ((data[(position + index) / 8] & (1 << ((position + index) % 8))) != 0) {
          bytes[index / 8] |= (byte) (1 << (index % 8));
        }
      }
      position += length;
      return bytes;
    }

    void writeBytes(byte[] bytes, int length) {
      byte[] reversed = new byte[bytes.length + 1];
      for (int index = 0; index < bytes.length; index++) {
        reversed[bytes.length - index] = bytes[index];
      }
      write(new BigInteger(reversed), length);
    }

    byte[] finish(int length) {
      return Arrays.copyOf(data, length);
    }
  }
}
