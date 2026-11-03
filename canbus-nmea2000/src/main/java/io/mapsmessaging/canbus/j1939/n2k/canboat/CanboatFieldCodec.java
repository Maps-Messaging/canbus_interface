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

import static io.mapsmessaging.canbus.j1939.n2k.canboat.CanboatValueCodec.bad;
import static io.mapsmessaging.canbus.j1939.n2k.canboat.CanboatValueCodec.binaryLength;
import static io.mapsmessaging.canbus.j1939.n2k.canboat.CanboatValueCodec.decimal;
import static io.mapsmessaging.canbus.j1939.n2k.canboat.CanboatValueCodec.mask;
import static io.mapsmessaging.canbus.j1939.n2k.canboat.CanboatValueCodec.numericRaw;
import static io.mapsmessaging.canbus.j1939.n2k.canboat.CanboatValueCodec.scalar;
import static io.mapsmessaging.canbus.j1939.n2k.canboat.CanboatValueCodec.string;
import static io.mapsmessaging.canbus.j1939.n2k.canboat.CanboatValueCodec.unsigned;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigInteger;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves and transfers one field, including dynamic/reference metadata. */
final class CanboatFieldCodec {
  private static final String CONTINUES = "Continues";
  private static final String BINARY = "BINARY";
  private static final String MATCH = "Match";
  private static final String BIT_LENGTH = "BitLength";
  private static final String SIGNED = "Signed";
  private static final String UNKNOWN_VALUE = "UnknownValue";
  private static final String RESERVED = "RESERVED";
  private static final String SPARE = "SPARE";

  private final CanboatDatabase database;

  CanboatFieldCodec(CanboatDatabase database) {
    this.database = database;
  }

  void value(CanboatMessage message, CanboatField original, JsonObject packet, CanboatCodecState state, boolean encode, boolean repeated) {
    CanboatFieldLayout layout = resolveLayout(message, original, packet, state, encode);
    String continues = original.text(CONTINUES);
    CanboatField continued = continues == null ? null : message.fields().stream().filter(field -> field.id().equals(continues)).findFirst().orElse(null);
    JsonElement input = packet.get(original.id());
    if (encode && continued != null && packet.has(continues) && !packet.get(continues).isJsonNull()) {
      input = new JsonPrimitive(packet.get(continues).getAsBigInteger().shiftRight(continued.number(BIT_LENGTH, 0)));
    }
    CanboatFieldValue result = transfer(message, original, layout, input, state, encode, repeated);
    if (result == null) {
      return; // A trailing fixed numeric field may be absent once the minimum payload is present.
    }
    BigInteger raw = result.raw();
    if (!encode) {
      addDecodedValue(message, original, packet, state, continued, layout, result);
    }
    if (raw != null) {
      updateState(message, original, state, raw);
    }
    transferPadding(state.bits, layout.referencePadding(), encode);
    if (layout.variableReference() && raw != null && state.fieldIndex != null) {
      state.parameters.put(state.fieldIndex, raw);
    }
    if (layout.dynamic()) {
      state.dynamic = null;
      state.dynamicLength = null;
    }
  }

  private CanboatFieldLayout resolveLayout(CanboatMessage message, CanboatField original, JsonObject packet, CanboatCodecState state, boolean encode) {
    CanboatField field = original;
    int bits = field.number(BIT_LENGTH, 0);
    boolean variableReference = field.type().equals("VARIABLE");
    int referencePadding = 0;
    boolean dynamic = field.type().equals("DYNAMIC_FIELD_VALUE");
    if (variableReference) {
      field = referenced(original, state);
      bits = field.number(BIT_LENGTH, 0);
      // Group-function parameter values occupy complete bytes even for bit fields.
      referencePadding = ((bits + 7) / 8) * 8 - bits;
    }
    if (dynamic) {
      field = dynamicField(original, state);
      bits = dynamicBits(original, field, packet, state, encode);
      field = dynamicSentinels(field, bits);
    }
    if (field.text("BitLengthField") != null) {
      BigInteger length = state.raw.get(field.number("BitLengthField", 0));
      if (length == null || length.signum() < 0 || length.compareTo(BigInteger.valueOf(8_000_000)) > 0) {
        throw bad(message, "invalid referenced bit length");
      }
      bits = length.intValue();
    }
    return new CanboatFieldLayout(field, bits, referencePadding, variableReference, dynamic);
  }

  private static CanboatField dynamicField(CanboatField original, CanboatCodecState state) {
    Map<String, String> metadata = new LinkedHashMap<>(original.properties());
    if (state.dynamic == null) {
      metadata.put("FieldType", BINARY);
    } else {
      metadata.putAll(state.dynamic);
      if (state.dynamic.containsKey("Bits")) {
        metadata.put(BIT_LENGTH, state.dynamic.get("Bits"));
      }
    }
    metadata.put("Id", original.id());
    metadata.remove(MATCH);
    return new CanboatField(metadata);
  }

  private static int dynamicBits(CanboatField original, CanboatField field, JsonObject packet, CanboatCodecState state, boolean encode) {
    if (state.dynamicLength != null) {
      return Math.max(0, state.dynamicLength - original.number("DynamicFieldLengthOverhead", 0)) * 8;
    }
    int bits = field.number(BIT_LENGTH, 0);
    if (bits == 0) {
      return encode ? binaryLength(packet.get(original.id())) * 8 : state.bits.remaining();
    }
    return bits;
  }

  private static CanboatField dynamicSentinels(CanboatField field, int bits) {
    if (bits < 8 || !java.util.Set.of("NUMBER", "DURATION", "TIME", "DATE", "ADDRESS", "PGN", "FIELD_INDEX").contains(field.type())) {
      return field;
    }
    Map<String, String> metadata = new LinkedHashMap<>(field.properties());
    BigInteger maximum = Boolean.parseBoolean(field.text(SIGNED)) ? mask(bits - 1) : mask(bits);
    metadata.putIfAbsent(UNKNOWN_VALUE, maximum.toString());
    metadata.putIfAbsent("OutOfRangeValue", maximum.subtract(BigInteger.ONE).toString());
    metadata.putIfAbsent("ReservedValue", maximum.subtract(BigInteger.TWO).toString());
    return new CanboatField(metadata);
  }

  private CanboatFieldValue transfer(
      CanboatMessage message, CanboatField original, CanboatFieldLayout layout, JsonElement input, CanboatCodecState state, boolean encode, boolean repeated) {
    CanboatField field = layout.field();
    int bits = layout.bits();
    String type = field.type();
    if (layout.dynamic() && bits == 0) {
      return new CanboatFieldValue(JsonNull.INSTANCE, null);
    }
    if (type.startsWith("STRING")) {
      return new CanboatFieldValue(string(field, input, state.bits, encode), null);
    }
    if (type.equals(BINARY) || bits > 64 && !type.equals("DECIMAL")) {
      return new CanboatFieldValue(binary(message, layout, input, state.bits, encode), null);
    }
    if (type.equals("DECIMAL")) {
      return new CanboatFieldValue(decimal(field, input, state.bits, bits, encode, state.raw), null);
    }
    if (bits <= 0 || bits > 64) {
      throw bad(message, "invalid width for " + original.id());
    }
    if (!encode && state.bits.remaining() < bits && !repeated && state.bits.bytesLength() >= message.minimum()) {
      return null;
    }
    BigInteger raw;
    JsonElement output;
    if (encode) {
      raw = encodedRaw(message, original, field, input, bits);
      state.bits.write(raw, bits);
      output = input;
    } else {
      raw = state.bits.read(bits);
      output = scalar(field, raw, bits);
    }
    return new CanboatFieldValue(output, raw);
  }

  private static JsonElement binary(CanboatMessage message, CanboatFieldLayout layout, JsonElement input, CanboatBits wire, boolean encode) {
    int bits = layout.bits();
    if (bits == 0 && layout.field().text(BIT_LENGTH) == null) {
      bits = encode ? binaryLength(input) * 8 : wire.remaining();
    }
    if (layout.dynamic() && !layout.field().type().equals(BINARY) && bits > 64) {
      if (encode) {
        throw bad(message, "cannot encode overwide dynamic numeric value");
      }
      wire.read(bits);
      return JsonNull.INSTANCE;
    }
    if (!encode) {
      return new JsonPrimitive(Base64.getEncoder().encodeToString(wire.readBytes(bits)));
    }
    byte[] bytes = input == null || input.isJsonNull() ? new byte[(bits + 7) / 8] : Base64.getDecoder().decode(input.getAsString());
    if (bytes.length * 8 < bits) {
      throw bad(message, "binary value is shorter than declared length");
    }
    wire.writeBytes(bytes, bits);
    return input;
  }

  private static BigInteger encodedRaw(CanboatMessage message, CanboatField original, CanboatField field, JsonElement input, int bits) {
    BigInteger raw = rawValue(message, original, field, input, bits);
    if (raw.signum() < 0) {
      raw = raw.add(BigInteger.ONE.shiftLeft(bits));
    }
    if (raw.signum() < 0 || raw.bitLength() > bits) {
      throw bad(message, "value outside wire range for " + original.id());
    }
    return raw;
  }

  private static BigInteger rawValue(CanboatMessage message, CanboatField original, CanboatField field, JsonElement input, int bits) {
    BigInteger match = original.integer(MATCH);
    if (match != null) {
      if (input != null && !input.isJsonNull() && !match.equals(numericRaw(field, input))) {
        throw bad(message, "conflicting discriminator " + original.id());
      }
      return match;
    }
    String type = field.type();
    if (type.equals(RESERVED) || type.equals(SPARE)) {
      return mask(bits);
    }
    if (original.type().equals("DYNAMIC_FIELD_LENGTH") && (input == null || input.isJsonNull())) {
      return BigInteger.ZERO;
    }
    if (input == null || input.isJsonNull()) {
      return missingRaw(field, bits);
    }
    if (type.equals("FLOAT")) {
      return bits == 32 ? BigInteger.valueOf(Integer.toUnsignedLong(Float.floatToIntBits(input.getAsFloat()))) : unsigned(Double.doubleToLongBits(input.getAsDouble()));
    }
    return numericFragment(message, original, field, input, bits);
  }

  private static BigInteger missingRaw(CanboatField field, int bits) {
    BigInteger unknown = field.integer(UNKNOWN_VALUE);
    if (unknown != null) {
      return unknown;
    }
    return Boolean.parseBoolean(field.text(SIGNED)) ? mask(bits - 1) : mask(bits);
  }

  private static BigInteger numericFragment(CanboatMessage message, CanboatField original, CanboatField field, JsonElement input, int bits) {
    BigInteger raw = numericRaw(field, input);
    int totalBits = bits;
    for (CanboatField fragment : message.fields()) {
      if (original.id().equals(fragment.text(CONTINUES))) {
        totalBits += fragment.number(BIT_LENGTH, 0);
      }
    }
    boolean signed = Boolean.parseBoolean(field.text(SIGNED));
    BigInteger minimum = signed ? BigInteger.ONE.shiftLeft(totalBits - 1).negate() : BigInteger.ZERO;
    BigInteger maximum = signed ? mask(totalBits - 1) : mask(totalBits);
    if (raw.compareTo(minimum) < 0 || raw.compareTo(maximum) > 0) {
      throw bad(message, "value outside wire range for " + original.id());
    }
    return totalBits > bits ? raw.and(mask(bits)) : raw;
  }

  private static void addDecodedValue(
      CanboatMessage message, CanboatField original, JsonObject packet, CanboatCodecState state, CanboatField continued, CanboatFieldLayout layout, CanboatFieldValue result) {
    if (continued != null && result.raw() != null) {
      BigInteger low = state.raw.get(continued.order());
      if (low == null) {
        throw bad(message, "continuation has no preceding field");
      }
      packet.addProperty(original.text(CONTINUES), low.or(result.raw().shiftLeft(continued.number(BIT_LENGTH, 0))));
    } else if (!layout.field().type().equals(RESERVED) && !layout.field().type().equals(SPARE)) {
      packet.add(original.id(), result.output() == null ? JsonNull.INSTANCE : result.output());
    }
  }

  private void updateState(CanboatMessage message, CanboatField original, CanboatCodecState state, BigInteger raw) {
    state.raw.put(original.order(), raw);
    switch (original.type()) {
      case "PGN" -> state.referencePgn = raw.intValue();
      case "FIELD_INDEX" -> state.fieldIndex = raw.intValue();
      case "DYNAMIC_FIELD_KEY" -> state.dynamic = database.lookup(original.text("LookupFieldTypeEnumeration"), raw.toString());
      case "DYNAMIC_FIELD_LENGTH" -> {
        if (raw.compareTo(BigInteger.valueOf(1_000_000)) > 0) {
          throw bad(message, "dynamic length is too large");
        }
        state.dynamicLength = raw.intValue() - original.number("DynamicFieldLengthOverhead", 0);
        if (state.dynamicLength < 0) {
          throw bad(message, "dynamic length smaller than overhead");
        }
      }
      default -> { /* Other scalar types do not change field interpretation. */
      }
    }
  }

  private static void transferPadding(CanboatBits wire, int bits, boolean encode) {
    if (bits > 0) {
      if (encode) {
        wire.write(mask(bits), bits);
      } else {
        wire.read(bits);
      }
    }
  }

  private CanboatField referenced(CanboatField original, CanboatCodecState state) {
    if (state.referencePgn == null || state.fieldIndex == null) {
      throw new IllegalArgumentException("Variable field lacks referenced PGN/field index");
    }
    for (CanboatMessage candidate : database.candidates(state.referencePgn)) {
      if (candidate.fallback()) {
        continue;
      }
      CanboatField target = candidate.field(state.fieldIndex);
      if (matchesParameters(candidate, state) && target != null) {
        Map<String, String> properties = new LinkedHashMap<>(target.properties());
        properties.put("Id", original.id());
        properties.remove(MATCH);
        return new CanboatField(properties);
      }
    }
    throw new IllegalArgumentException("Unknown referenced field " + state.fieldIndex + " in PGN " + state.referencePgn);
  }

  private static boolean matchesParameters(CanboatMessage candidate, CanboatCodecState state) {
    for (CanboatField field : candidate.fields()) {
      BigInteger match = field.integer(MATCH);
      if (match != null && state.parameters.containsKey(field.order()) && !state.parameters.get(field.order()).equals(match)) {
        return false;
      }
    }
    return true;
  }
}
