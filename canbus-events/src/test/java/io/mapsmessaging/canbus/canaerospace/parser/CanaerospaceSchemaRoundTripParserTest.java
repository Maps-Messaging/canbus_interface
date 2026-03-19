package io.mapsmessaging.canbus.canaerospace.parser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.canaerospace.schema.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class CanaerospaceSchemaRoundTripParserTest {

  private static CanaerospaceFrameParser frameParser;

  @Test
  void roundTripAllSchemaPacketsToJson() throws Exception {
    CanaerospaceSchemaRegistry registry = CanaerospaceSchemaRegistry.loadFromClasspath();
    CanaerospaceSchema schema = registry.getSchema();

    Assertions.assertNotNull(schema);

    Map<String, DataTypeEntry> dataTypesByName = extractDataTypesByName(schema);
    Assertions.assertFalse(dataTypesByName.isEmpty(), "Schema dataTypes.byName is empty");

    List<IdentifierDefinition> concreteIdentifiers = Optional.ofNullable(schema.getIdentifiers())
        .orElseGet(Collections::emptyList);

    List<ExpandedIdentifier> allIdentifiers = new ArrayList<>();

    for (IdentifierDefinition identifier : concreteIdentifiers) {
      allIdentifiers.add(ExpandedIdentifier.fromConcrete(identifier));
    }

    for (ExpandedIdentifier expanded : expandIdentifierRanges(schema)) {
      allIdentifiers.add(expanded);
    }

    Assertions.assertFalse(allIdentifiers.isEmpty(), "No identifiers (concrete or ranged) found");

    AtomicInteger executed = new AtomicInteger(0);
    AtomicInteger validated = new AtomicInteger(0);

    for (ExpandedIdentifier identifier : allIdentifiers) {
      if (identifier.dataTypeName == null || identifier.dataTypeName.isBlank()) {
        continue;
      }

      DataTypeEntry dataTypeEntry = dataTypesByName.get(identifier.dataTypeName);
      if (dataTypeEntry == null) {
        continue;
      }

      Integer typeNumber = dataTypeEntry.getTypeNumber();
      if (typeNumber == null) {
        continue;
      }

      byte[] payload = buildPayload(
          typeNumber,
          identifier.dataTypeName,
          identifier.rangeMin,
          identifier.rangeMax,
          identifier.resolution
      );

      JsonObject json = parseToJson(identifier.id, payload);
      Assertions.assertNotNull(json, "Parser returned null JSON for id=" + identifier.id);

      executed.incrementAndGet();

      validateJsonBasics(json, identifier);

      if (tryValidateNumericValue(json, identifier)) {
        validated.incrementAndGet();
      }
    }

    Assertions.assertTrue(executed.get() > 0, "No packets were executed through the parser");
    System.out.println("Executed packets: " + executed.get() + ", numeric validations: " + validated.get());
  }

  /**
   * Expected payload layout (DLC=8):
   *   [0]=nodeId, [1]=dataType, [2]=serviceCode, [3]=messageCode, [4..7]=data (big-endian)
   */
  private static JsonObject parseToJson(int canId, byte[] payload) throws Exception {
    if (frameParser == null) {
      CanaerospaceSchemaRegistry registry = CanaerospaceSchemaRegistry.loadFromClasspath();
      frameParser = new CanaerospaceFrameParser(registry);
    }

    ParsedCanaerospaceMessage parsed = frameParser.parse(canId, payload);


    JsonObject json = new JsonObject();
    json.addProperty("canId", parsed.getCanId());
    json.addProperty("messageType", parsed.getMessageType());

    json.addProperty("nodeId", parsed.getNodeId());
    json.addProperty("payloadDataTypeNumber", parsed.getPayloadDataTypeNumber());
    json.addProperty("payloadDataTypeName", parsed.getPayloadDataTypeName());
    json.addProperty("serviceCode", parsed.getServiceCode());
    json.addProperty("messageCode", parsed.getMessageCode());

    json.addProperty("group", parsed.getGroup());
    json.addProperty("title", parsed.getTitle());
    json.addProperty("name", parsed.getName());
    json.addProperty("schemaDataType", parsed.getSchemaDataType());
    json.addProperty("units", parsed.getUnits());
    json.addProperty("resolution", parsed.getResolution());
    json.addProperty("notes", parsed.getNotes());
    json.addProperty("rangeMin", parsed.getRangeMin());
    json.addProperty("rangeMax", parsed.getRangeMax());

    Object raw = parsed.getRawValue();
    if (raw instanceof Number number) {
      json.addProperty("rawValue", number.doubleValue());
    } else if (raw != null) {
      json.addProperty("rawValue", raw.toString());
    }

    if (parsed.getEngineeringValue() != null) {
      json.addProperty("value", parsed.getEngineeringValue());
    }

    json.addProperty("dataTypeMismatch", parsed.isDataTypeMismatch());

    return json;
  }

  private static byte[] buildPayload(
      int dataTypeNumber,
      String dataTypeName,
      Double rangeMin,
      Double rangeMax,
      Double resolution
  ) {
    byte nodeId = 1;
    byte serviceCode = 0;
    byte messageCode = 0;

    byte[] payload = new byte[8];
    payload[0] = nodeId;
    payload[1] = (byte) (dataTypeNumber & 0xFF);
    payload[2] = serviceCode;
    payload[3] = messageCode;

    byte[] dataBytes = synthesizeDataBytes(dataTypeName, rangeMin, rangeMax, resolution);

    payload[4] = dataBytes[0];
    payload[5] = dataBytes[1];
    payload[6] = dataBytes[2];
    payload[7] = dataBytes[3];

    return payload;
  }

  private static byte[] synthesizeDataBytes(String dataTypeName, Double rangeMin, Double rangeMax, Double resolution) {
    byte[] out = new byte[4];

    double chosenEngineeringValue = chooseEngineeringValue(rangeMin, rangeMax);
    chosenEngineeringValue = clampEngineeringToRawWidth(dataTypeName, resolution, chosenEngineeringValue);

    switch (dataTypeName) {
      case "SHORT" -> {
        int raw = toRawSigned(chosenEngineeringValue, resolution);
        Assertions.assertTrue(raw >= Short.MIN_VALUE && raw <= Short.MAX_VALUE,
            "SHORT raw overflow for raw=" + raw + " value=" + chosenEngineeringValue + " res=" + resolution);
        putInt16BigEndian(out, (short) raw);
      }
      case "USHORT" -> {
        int raw = toRawUnsigned(chosenEngineeringValue, resolution);
        putUInt16BigEndian(out, raw);
      }
      case "LONG" -> {
        long raw = toRawSignedLong(chosenEngineeringValue, resolution);
        putInt32BigEndian(out, (int) raw);
      }
      case "ULONG" -> {
        long raw = toRawUnsignedLong(chosenEngineeringValue, resolution);
        putUInt32BigEndian(out, raw);
      }
      case "FLOAT" -> {
        float f = (float) chosenEngineeringValue;
        putFloat32BigEndian(out, f);
      }
      case "VARIABLE3" -> {
        int raw = toRawSigned(chosenEngineeringValue, resolution);
        putInt24BigEndian(out, raw);
      }
      case "UVARIABLE3" -> {
        int raw = toRawUnsigned(chosenEngineeringValue, resolution);
        putUInt24BigEndian(out, raw);
      }
      case "CHAR", "UCHAR", "ACHAR" -> {
        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        out[3] = 42;
      }
      case "CHAR2", "UCHAR2", "ACHAR2" -> {
        out[0] = 0;
        out[1] = 0;
        out[2] = 1;
        out[3] = 2;
      }
      case "CHAR4", "UCHAR4", "ACHAR4" -> {
        out[0] = 1;
        out[1] = 2;
        out[2] = 3;
        out[3] = 4;
      }
      case "BLONG", "BSHORT", "BCHAR", "BSHORT2", "BCHAR2", "BCHAR4" -> {
        out[0] = (byte) 0xAA;
        out[1] = (byte) 0x55;
        out[2] = (byte) 0x0F;
        out[3] = (byte) 0xF0;
      }
      default -> {
        putUInt32BigEndian(out, 123456789L);
      }
    }

    return out;
  }

  private static double clampEngineeringToRawWidth(String dataTypeName, Double resolution, double engineeringValue) {
    if (resolution == null || resolution == 0.0) {
      return engineeringValue;
    }

    long minRaw;
    long maxRaw;

    switch (dataTypeName) {
      case "SHORT" -> {
        minRaw = Short.MIN_VALUE;
        maxRaw = Short.MAX_VALUE;
      }
      case "USHORT", "BSHORT" -> {
        minRaw = 0;
        maxRaw = 0xFFFFL;
      }
      case "VARIABLE3" -> {
        minRaw = -(1L << 23);
        maxRaw = (1L << 23) - 1;
      }
      case "UVARIABLE3" -> {
        minRaw = 0;
        maxRaw = (1L << 24) - 1;
      }
      case "LONG" -> {
        minRaw = Integer.MIN_VALUE;
        maxRaw = Integer.MAX_VALUE;
      }
      case "ULONG", "BLONG", "MEMID", "CHKSUM" -> {
        minRaw = 0;
        maxRaw = 0xFFFFFFFFL;
      }
      default -> {
        return engineeringValue;
      }
    }

    double minEng = minRaw * resolution;
    double maxEng = maxRaw * resolution;

    if (engineeringValue < minEng) {
      return minEng;
    }
    if (engineeringValue > maxEng) {
      return maxEng;
    }
    return engineeringValue;
  }


  private static double chooseEngineeringValue(Double min, Double max) {
    if (min == null && max == null) {
      return 1.0;
    }
    if (min != null && max != null) {
      return (min + max) / 2.0;
    }
    if (min != null) {
      return min;
    }
    return max;
  }

  private static int toRawSigned(double engineeringValue, Double resolution) {
    if (resolution == null || resolution == 0.0) {
      return (int) Math.round(engineeringValue);
    }
    return (int) Math.round(engineeringValue / resolution);
  }

  private static long toRawSignedLong(double engineeringValue, Double resolution) {
    if (resolution == null || resolution == 0.0) {
      return Math.round(engineeringValue);
    }
    return Math.round(engineeringValue / resolution);
  }

  private static int toRawUnsigned(double engineeringValue, Double resolution) {
    return Math.max(0, toRawSigned(engineeringValue, resolution));
  }

  private static long toRawUnsignedLong(double engineeringValue, Double resolution) {
    return Math.max(0L, toRawSignedLong(engineeringValue, resolution));
  }

  private static void validateJsonBasics(JsonObject json, ExpandedIdentifier identifier) {
    Assertions.assertTrue(json.size() > 0, "JSON was empty for id=" + identifier.id);

    if (json.has("canId")) {
      Assertions.assertEquals(identifier.id, json.get("canId").getAsInt(), "JSON canId mismatch");
    }
    if (identifier.name != null && json.has("name")) {
      Assertions.assertEquals(identifier.name, json.get("name").getAsString(), "JSON name mismatch on "+identifier.id);
    }
  }

  private static boolean tryValidateNumericValue(JsonObject json, ExpandedIdentifier identifier) {
    if (identifier.resolution == null) {
      return false;
    }

    JsonElement valueElement = json.get("value");
    if (valueElement == null || !valueElement.isJsonPrimitive() || !valueElement.getAsJsonPrimitive().isNumber()) {
      return false;
    }

    double expected = decodeExpectedEngineeringValue(identifier);
    double actual = valueElement.getAsDouble();

    double tolerance = Math.abs(identifier.resolution) * 2.0;
    double delta = Math.abs(actual - expected);

    Assertions.assertTrue(
        delta <= tolerance,
        "Value mismatch for id=" + identifier.id
            + " expected~" + expected
            + " actual=" + actual
            + " tol=" + tolerance
    );

    return true;
  }

  private static double decodeExpectedEngineeringValue(ExpandedIdentifier identifier) {
    double chosenEngineeringValue = chooseEngineeringValue(identifier.rangeMin, identifier.rangeMax);
    return clampEngineeringToRawWidth(identifier.dataTypeName, identifier.resolution, chosenEngineeringValue);
  }

  private static Map<String, DataTypeEntry> extractDataTypesByName(CanaerospaceSchema schema) {
    DataTypesDefinition dataTypes = schema.getDataTypes();
    if (dataTypes == null) {
      return Collections.emptyMap();
    }
    Map<String, DataTypeEntry> byName = dataTypes.getByName();
    if (byName == null) {
      return Collections.emptyMap();
    }
    return byName;
  }

  private static List<ExpandedIdentifier> expandIdentifierRanges(CanaerospaceSchema schema) {
    Object identifierRanges = invokeGetterIfPresent(schema, "getIdentifierRanges");
    if (!(identifierRanges instanceof List<?> list)) {
      return Collections.emptyList();
    }

    List<ExpandedIdentifier> expanded = new ArrayList<>();
    for (Object rangeObj : list) {
      IdentifierRangeDefinition rangeDef = (IdentifierRangeDefinition) rangeObj;

      IdRange idRange = rangeDef.getIdRange();
      if (idRange == null || idRange.getMin() == null || idRange.getMax() == null) {
        continue;
      }

      int min = idRange.getMin().intValue();
      int max = idRange.getMax().intValue();

      Double rangeMin = rangeDef.getRange() == null || rangeDef.getRange().getMin() == null ? null : rangeDef.getRange().getMin().doubleValue();
      Double rangeMax = rangeDef.getRange() == null || rangeDef.getRange().getMax() == null ? null : rangeDef.getRange().getMax().doubleValue();
      Double resolution = rangeDef.getResolution();

      for (int id = min; id <= max; id++) {
        ExpandedIdentifier e = ExpandedIdentifier.fromRange(rangeDef, id, (id - min) + 1, rangeMin, rangeMax, resolution);
        expanded.add(e);
      }
    }
    return expanded;
  }

  private static Object invokeGetterIfPresent(Object target, String methodName) {
    try {
      Method m = target.getClass().getMethod(methodName);
      return m.invoke(target);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void putInt16BigEndian(byte[] out, short value) {
    out[0] = 0;
    out[1] = 0;
    out[2] = (byte) ((value >> 8) & 0xFF);
    out[3] = (byte) (value & 0xFF);
  }

  private static void putUInt16BigEndian(byte[] out, int value) {
    out[0] = 0;
    out[1] = 0;
    out[2] = (byte) ((value >> 8) & 0xFF);
    out[3] = (byte) (value & 0xFF);
  }

  private static void putInt32BigEndian(byte[] out, int value) {
    out[0] = (byte) ((value >> 24) & 0xFF);
    out[1] = (byte) ((value >> 16) & 0xFF);
    out[2] = (byte) ((value >> 8) & 0xFF);
    out[3] = (byte) (value & 0xFF);
  }

  private static void putUInt32BigEndian(byte[] out, long value) {
    out[0] = (byte) ((value >> 24) & 0xFF);
    out[1] = (byte) ((value >> 16) & 0xFF);
    out[2] = (byte) ((value >> 8) & 0xFF);
    out[3] = (byte) (value & 0xFF);
  }

  private static void putFloat32BigEndian(byte[] out, float value) {
    ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
    buffer.putFloat(value);
    byte[] bytes = buffer.array();
    out[0] = bytes[0];
    out[1] = bytes[1];
    out[2] = bytes[2];
    out[3] = bytes[3];
  }

  private static void putInt24BigEndian(byte[] out, int value) {
    out[0] = 0;
    out[1] = (byte) ((value >> 16) & 0xFF);
    out[2] = (byte) ((value >> 8) & 0xFF);
    out[3] = (byte) (value & 0xFF);
  }

  private static void putUInt24BigEndian(byte[] out, int value) {
    int v = Math.max(value, 0) & 0x00FFFFFF;
    out[0] = 0;
    out[1] = (byte) ((v >> 16) & 0xFF);
    out[2] = (byte) ((v >> 8) & 0xFF);
    out[3] = (byte) (v & 0xFF);
  }

  private static final class ExpandedIdentifier {

    private final int id;
    private final String name;
    private final String dataTypeName;
    private final Double rangeMin;
    private final Double rangeMax;
    private final Double resolution;

    private ExpandedIdentifier(int id, String name, String dataTypeName, Double rangeMin, Double rangeMax, Double resolution) {
      this.id = id;
      this.name = name;
      this.dataTypeName = dataTypeName;
      this.rangeMin = rangeMin;
      this.rangeMax = rangeMax;
      this.resolution = resolution;
    }

    static ExpandedIdentifier fromConcrete(IdentifierDefinition identifier) {
      Double min = identifier.getRange() == null || identifier.getRange().getMin() == null ? null : identifier.getRange().getMin().doubleValue();
      Double max = identifier.getRange() == null || identifier.getRange().getMax() == null ? null : identifier.getRange().getMax().doubleValue();

      return new ExpandedIdentifier(
          identifier.getId(),
          identifier.getName(),
          identifier.getDataType(),
          min,
          max,
          identifier.getResolution()
      );
    }

    static ExpandedIdentifier fromRange(
        IdentifierRangeDefinition rangeDef,
        int id,
        int ordinal,
        Double rangeMin,
        Double rangeMax,
        Double resolution
    ) {
      String template = rangeDef.getTitleTemplate();
      String name = template == null ? null : template.replace("{n}", String.valueOf(ordinal));

      return new ExpandedIdentifier(
          id,
          name,
          rangeDef.getDataType(),
          rangeMin,
          rangeMax,
          resolution
      );
    }
  }
}