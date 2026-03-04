package io.mapsmessaging.canbus.canaerospace.schema;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;


class CanaerospaceSchemaRegistryTest {

  @Test
  void loadFromClasspathAndDump() throws Exception {
    CanaerospaceSchemaRegistry registry = CanaerospaceSchemaRegistry.loadFromClasspath();
    dumpSchema(registry.getSchema());
  }

  @Test
  void loadFromFileAndDump() throws Exception {
    Path yamlPath = Path.of("src/main/resources/canaerospace_schema.yaml");
    CanaerospaceSchemaRegistry registry = CanaerospaceSchemaRegistry.load(yamlPath);
    dumpSchema(registry.getSchema());
  }

  private static void dumpSchema(CanaerospaceSchema schema) {
    Assertions.assertNotNull(schema);

    System.out.println("=== CANaerospace Schema Dump ===");

    if (schema.getMeta() != null) {
      System.out.println("meta.schema=" + schema.getMeta().getSchema());
      System.out.println("meta.version=" + schema.getMeta().getVersion());
      System.out.println("meta.generated=" + schema.getMeta().getGenerated());
    }

    List<IdentifierDefinition> identifiers = schema.getIdentifiers();
    System.out.println("identifiers.count=" + (identifiers == null ? 0 : identifiers.size()));
    if (identifiers != null && !identifiers.isEmpty()) {
      for (int index = 0; index < identifiers.size(); index++) {
        IdentifierDefinition entry = identifiers.get(index);
        System.out.println("identifier[" + index + "]: id=" + entry.getId()
            + " hex=" + entry.getHex()
            + " name=" + entry.getName()
            + " dataType=" + entry.getDataType()
            + " units=" + entry.getUnits()
            + " range=" + formatRange(entry.getRange())
            + " resolution=" + entry.getResolution());
      }
    }

    DataTypesDefinition dataTypes = schema.getDataTypes();
    if (dataTypes != null) {
      Map<String, DataTypeEntry> byName = dataTypes.getByName();
      System.out.println("dataTypes.byName.count=" + (byName == null ? 0 : byName.size()));
      if (byName != null && !byName.isEmpty()) {
        DataTypeEntry shortType = byName.get("SHORT");
        if (shortType != null) {
          System.out.println("dataType.SHORT.range=" + formatRange(shortType.getRange()));
        }
      }
    }


      System.out.println("=== End Schema Dump ===");
  }

  private static String formatRange(NumericRange range) {
    if (range == null) {
      return "null";
    }
    return "[" + range.getMin() + ", " + range.getMax() + "]";
  }
}