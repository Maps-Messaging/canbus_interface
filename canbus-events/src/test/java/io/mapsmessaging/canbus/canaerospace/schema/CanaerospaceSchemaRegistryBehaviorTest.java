/*
 *   Copyright [ 2024 - 2026 ] MapsMessaging B.V.
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
 */
package io.mapsmessaging.canbus.canaerospace.schema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanaerospaceSchemaRegistryBehaviorTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void explicitIdentifierWinsOverExpandedRangeAndDuplicateWithMessageTypeWins() {
    CanaerospaceSchema schema = new CanaerospaceSchema();
    IdentifierRangeDefinition range = new IdentifierRangeDefinition();
    range.setIdRange(idRange(100, 102));
    range.setTitleTemplate("Sensor {n}");
    range.setDataType("FLOAT");
    schema.setIdentifierRanges(List.of(range));

    IdentifierDefinition incomplete = new IdentifierDefinition();
    incomplete.setId(101);
    incomplete.setTitle("Old");
    IdentifierDefinition preferred = new IdentifierDefinition();
    preferred.setId(101);
    preferred.setTitle("Navigation");
    preferred.setMessageType("DATA");
    schema.setIdentifiers(List.of(incomplete, preferred));

    CanaerospaceSchemaRegistry registry = new CanaerospaceSchemaRegistry(schema);

    assertEquals("Sensor 1", registry.findIdentifier(100).orElseThrow().getTitle());
    assertEquals("Navigation", registry.findIdentifier(101).orElseThrow().getTitle());
    assertEquals("DATA", registry.findIdentifier(101).orElseThrow().getMessageType());
    assertEquals("Sensor 3", registry.findIdentifier(102).orElseThrow().getTitle());
    assertFalse(registry.findIdentifier(103).isPresent());
  }

  @Test
  void messageTypesAndDataTypeNumbersResolveWithinTheirRanges() {
    CanaerospaceSchema schema = new CanaerospaceSchema();
    MessageTypeDefinition messageType = new MessageTypeDefinition();
    messageType.setIdRange(idRange(200, 202));
    schema.setMessageTypes(List.of(messageType));
    DataTypesDefinition dataTypes = new DataTypesDefinition();
    dataTypes.setByNumber(Map.of(1, "FLOAT"));
    schema.setDataTypes(dataTypes);

    CanaerospaceSchemaRegistry registry = new CanaerospaceSchemaRegistry(schema);

    assertEquals(messageType, registry.findMessageType(200).orElseThrow());
    assertEquals(messageType, registry.findMessageType(202).orElseThrow());
    assertFalse(registry.findMessageType(203).isPresent());
    assertEquals("FLOAT", registry.findDataTypeNameByNumber(1).orElseThrow());
    assertFalse(registry.findDataTypeNameByNumber(2).isPresent());
  }

  @Test
  void rejectsInvalidCanIdentifierRanges() {
    for (IdRange bounds : List.of(idRange(-1, 2), idRange(2047, 2048), idRange(4, 3))) {
      CanaerospaceSchema schema = new CanaerospaceSchema();
      IdentifierRangeDefinition range = new IdentifierRangeDefinition();
      range.setIdRange(bounds);
      schema.setIdentifierRanges(List.of(range));

      IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
          () -> new CanaerospaceSchemaRegistry(schema));
      assertTrue(failure.getMessage().contains("Invalid CANaerospace identifier range"));
    }
  }

  @Test
  void rejectsMissingAndEmptySchemas() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> new CanaerospaceSchemaRegistry(null));
    assertThrows(IllegalArgumentException.class, () -> CanaerospaceSchemaRegistry.load(null));
    assertThrows(IllegalArgumentException.class,
        () -> CanaerospaceSchemaRegistry.load(temporaryDirectory.resolve("missing.yaml")));
    assertThrows(IllegalArgumentException.class,
        () -> CanaerospaceSchemaRegistry.loadFromClasspath("missing-schema.yaml"));
    Path empty = temporaryDirectory.resolve("empty.yaml");
    Files.writeString(empty, "");
    assertThrows(IllegalStateException.class, () -> CanaerospaceSchemaRegistry.load(empty));
  }

  private static IdRange idRange(int min, int max) {
    IdRange range = new IdRange();
    range.setMin(min);
    range.setMax(max);
    return range;
  }
}
