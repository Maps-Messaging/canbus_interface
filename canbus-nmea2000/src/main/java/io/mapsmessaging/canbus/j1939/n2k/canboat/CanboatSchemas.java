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
import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON schemas for the decoded CANboat envelope, retaining every named PGN variant. */
public final class CanboatSchemas {
  private CanboatSchemas() {}

  public static Map<Integer, JsonObject> build(CanboatDatabase database) {
    Map<Integer, JsonObject> schemas = new LinkedHashMap<>();
    for (CanboatMessage message : database.getMessages()) {
      schemas.computeIfAbsent(message.pgn(), pgn -> schema(database, pgn));
    }
    return Map.copyOf(schemas);
  }

  public static JsonObject schema(CanboatDatabase database, int pgn) {
    List<CanboatMessage> variants = new ArrayList<>(database.variants(pgn));
    if (variants.isEmpty()) {
      throw new IllegalArgumentException("Unknown PGN: " + pgn);
    }
    CanboatMessage fallback = database.fallback(pgn);
    boolean conditionalOnly =
        variants.stream()
            .allMatch(
                message ->
                    !message.fallback()
                        && message.fields().stream()
                            .anyMatch(field -> field.integer("Match") != null));
    if (conditionalOnly && fallback != null) {
      variants.add(fallback);
    }
    JsonObject root = object();
    root.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
    root.addProperty("$id", database.getProtocol() + "/pgn/" + pgn + ".schema.json");
    root.addProperty("title", database.getProtocol() + " PGN " + pgn);
    JsonObject properties = new JsonObject();
    properties.add("pgn", constant(pgn));
    JsonObject name = new JsonObject();
    name.addProperty("type", "string");
    JsonArray names = new JsonArray();
    JsonArray alternatives = new JsonArray();
    JsonObject mergedPacket = object();
    JsonObject mergedFields = new JsonObject();
    for (CanboatMessage message : variants) {
      names.add(message.id());
      JsonObject packet = packet(message);
      for (Map.Entry<String, JsonElement> entry : packet.getAsJsonObject("properties").entrySet()) {
        JsonElement previous = mergedFields.get(entry.getKey());
        if (previous == null) {
          mergedFields.add(entry.getKey(), entry.getValue().deepCopy());
        } else if (!previous.equals(entry.getValue())) {
          JsonObject union = new JsonObject();
          JsonArray choices = new JsonArray();
          choices.add(previous.deepCopy());
          choices.add(entry.getValue().deepCopy());
          union.add("anyOf", choices);
          mergedFields.add(entry.getKey(), union);
        }
      }
      JsonObject variant = object();
      variant.addProperty("title", message.id());
      if (message.properties().containsKey("Description")) {
        variant.addProperty("description", message.properties().get("Description"));
      }
      JsonObject variantProperties = new JsonObject();
      variantProperties.add("name", constant(message.id()));
      variantProperties.add("packet", packet);
      variantProperties.add("pgn", constant(pgn));
      variant.add("properties", variantProperties);
      variant.add("required", strings("packet", "name"));
      variant.addProperty("additionalProperties", false);
      alternatives.add(variant);
    }
    name.add("enum", names);
    properties.add("name", name);
    mergedPacket.add("properties", mergedFields);
    mergedPacket.addProperty("additionalProperties", false);
    properties.add(
        "packet",
        variants.size() == 1
            ? alternatives
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("properties")
                .get("packet")
                .deepCopy()
            : mergedPacket);
    root.add("properties", properties);
    root.add("required", strings("packet", "name"));
    root.addProperty("additionalProperties", false);
    if (variants.size() > 1) {
      root.add("oneOf", alternatives);
    }
    return root;
  }

  private static JsonObject packet(CanboatMessage message) {
    JsonObject packet = object();
    JsonObject properties = new JsonObject();
    for (CanboatField field : message.fields()) {
      if (!visible(field) || group(message, field.order()) != 0) {
        continue;
      }
      properties.add(field.id(), field(field));
    }
    for (int index = 1; index <= 2; index++) {
      int size = message.number("RepeatingFieldSet" + index + "Size", 0);
      if (size <= 0) {
        continue;
      }
      int start = message.number("RepeatingFieldSet" + index + "StartField", 0);
      JsonObject array = new JsonObject();
      array.addProperty("type", "array");
      JsonObject items = object();
      JsonObject itemProperties = new JsonObject();
      for (CanboatField field : message.fields()) {
        if (field.order() >= start && field.order() < start + size && visible(field)) {
          itemProperties.add(field.id(), field(field));
        }
      }
      items.add("properties", itemProperties);
      items.addProperty("additionalProperties", false);
      array.add("items", items);
      properties.add(index == 1 ? "list" : "list2", array);
    }
    // Fixed fields can be absent in truncated payloads, and conditional fields need not exist.
    packet.add("properties", properties);
    packet.addProperty("additionalProperties", false);
    return packet;
  }

  private static int group(CanboatMessage message, int order) {
    for (int index = 1; index <= 2; index++) {
      int start = message.number("RepeatingFieldSet" + index + "StartField", 0);
      int size = message.number("RepeatingFieldSet" + index + "Size", 0);
      if (size > 0 && order >= start && order < start + size) {
        return index;
      }
    }
    return 0;
  }

  private static boolean visible(CanboatField field) {
    return !field.type().equals("RESERVED")
        && !field.type().equals("SPARE")
        && field.text("Continues") == null;
  }

  private static JsonObject field(CanboatField field) {
    JsonObject schema = new JsonObject();
    switch (field.type()) {
      case "NUMBER", "DURATION", "TIME", "DATE", "FLOAT" ->
          schema.add("type", strings("number", "null"));
      case "LOOKUP",
          "INDIRECT_LOOKUP",
          "BITLOOKUP",
          "PGN",
          "ADDRESS",
          "FIELD_INDEX",
          "DYNAMIC_FIELD_KEY",
          "DYNAMIC_FIELD_LENGTH",
          "ISO_NAME" ->
          schema.add("type", strings("integer", "null"));
      case "MMSI", "DECIMAL", "STRING_FIX", "STRING_LAU", "STRING_LZ" ->
          schema.add("type", strings("string", "null"));
      case "BINARY" -> {
        schema.add("type", strings("string", "null"));
        schema.addProperty("contentEncoding", "base64");
      }
      case "VARIABLE", "DYNAMIC_FIELD_VALUE" ->
          schema.add("type", strings("number", "string", "null"));
      default ->
          throw new IllegalArgumentException(
              "Unsupported CANboat schema field type: " + field.type());
    }
    if (field.type().equals("ISO_NAME")) {
      schema.addProperty("minimum", 0);
      schema.addProperty("maximum", new BigInteger("18446744073709551615"));
    }
    if (field.text("Name") != null) {
      schema.addProperty("title", field.text("Name"));
    }
    if (field.text("Description") != null) {
      schema.addProperty("description", field.text("Description"));
    }
    if (field.text("Unit") != null) {
      schema.addProperty("unit", field.text("Unit"));
    }
    schema.addProperty("x-fieldType", field.type());
    if (field.text("Condition") != null) {
      schema.addProperty("x-condition", field.text("Condition"));
    }
    return schema;
  }

  private static JsonObject object() {
    JsonObject object = new JsonObject();
    object.addProperty("type", "object");
    return object;
  }

  private static JsonObject constant(Number value) {
    JsonObject constant = new JsonObject();
    constant.addProperty("const", value);
    return constant;
  }

  private static JsonObject constant(String value) {
    JsonObject constant = new JsonObject();
    constant.addProperty("const", value);
    return constant;
  }

  private static JsonArray strings(String... values) {
    JsonArray array = new JsonArray();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }
}
