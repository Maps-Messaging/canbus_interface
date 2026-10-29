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

package io.mapsmessaging.canbus.j1939.n2k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.j1939.n2k.schema.N2kSchemaRegistry;
import org.junit.jupiter.api.Test;

class CanboatSchemaTest {
  @Test
  void variantsHaveDistinctEnvelopeNamesAndTwoTypedRepeatGroups() throws Exception {
    JsonObject schema = new N2kSchemaRegistry(N2kParserFactory.getN2kParser()).getSchema(126208);
    assertEquals(126208, properties(schema).getAsJsonObject("pgn").get("const").getAsInt());
    assertFalse(
        schema.getAsJsonArray("required").contains(new com.google.gson.JsonPrimitive("pgn")));
    JsonObject read = variant(schema, "nmeaReadFieldsGroupFunction");
    JsonObject packetProperties = properties(properties(read).getAsJsonObject("packet"));
    JsonObject list = packetProperties.getAsJsonObject("list");
    assertEquals("array", list.get("type").getAsString());
    assertNotNull(properties(list.getAsJsonObject("items")).get("selectionValue"));
    assertNotNull(
        properties(packetProperties.getAsJsonObject("list2").getAsJsonObject("items"))
            .get("parameter"));
    assertFalse(packetProperties.has("selectionValue"));
    assertFalse(packetProperties.has("reserved"));
    assertNotNull(packetProperties.get("manufacturerCode"));
  }

  @Test
  void describesTextBinaryAndExactIsoNameWithoutInventingNumericFields() throws Exception {
    N2kSchemaRegistry schemas = new N2kSchemaRegistry(N2kParserFactory.getN2kParser());
    JsonObject alert = properties(properties(schemas.getSchema(126983)).getAsJsonObject("packet"));
    JsonObject name = alert.getAsJsonObject("dataSourceNetworkIdName");
    assertEquals("18446744073709551615", name.get("maximum").getAsString());
    assertTrue(name.getAsJsonArray("type").contains(new com.google.gson.JsonPrimitive("null")));
    JsonObject config = properties(properties(schemas.getSchema(126998)).getAsJsonObject("packet"));
    assertTrue(
        config
            .getAsJsonObject("installationDescription1")
            .getAsJsonArray("type")
            .contains(new com.google.gson.JsonPrimitive("string")));
    JsonObject fallback =
        variant(
            schemas.getSchema(130816), "0x1ff000x1ffffManufacturerSpecificFastPacketNonAddressed");
    JsonObject data =
        properties(properties(fallback).getAsJsonObject("packet")).getAsJsonObject("data");
    assertEquals("base64", data.get("contentEncoding").getAsString());
  }

  @Test
  void j1939DtcSchemaIncludesCombinedSpnOnlyInRepeatedRows() throws Exception {
    JsonObject schema = new N2kSchemaRegistry(N2kParserFactory.getJ1939Parser()).getSchema(65226);
    JsonObject fields = properties(properties(schema).getAsJsonObject("packet"));
    JsonObject row = properties(fields.getAsJsonObject("list").getAsJsonObject("items"));
    assertNotNull(row.get("spn"));
    assertFalse(row.has("spnHigh"));
    assertFalse(fields.has("spn"));
  }

  @Test
  void unmatchedManufacturerFallbackIsRepresentedForKnownPgn() throws Exception {
    JsonObject schema = new N2kSchemaRegistry(N2kParserFactory.getN2kParser()).getSchema(130945);
    JsonObject fallback =
        variant(schema, "0x1ff000x1ffffManufacturerSpecificFastPacketNonAddressed");
    assertEquals(
        "base64",
        properties(properties(fallback).getAsJsonObject("packet"))
            .getAsJsonObject("data")
            .get("contentEncoding")
            .getAsString());
  }

  private static JsonObject properties(JsonObject schema) {
    return schema.getAsJsonObject("properties");
  }

  private static JsonObject variant(JsonObject schema, String id) {
    for (JsonElement value : schema.getAsJsonArray("oneOf")) {
      JsonObject variant = value.getAsJsonObject();
      if (properties(variant).getAsJsonObject("name").get("const").getAsString().equals(id)) {
        return variant;
      }
    }
    throw new AssertionError("Missing variant " + id);
  }
}
