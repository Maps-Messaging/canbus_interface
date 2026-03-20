package io.mapsmessaging.canbus.canaerospace.parser;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.mapsmessaging.canbus.canaerospace.schema.CanaerospaceSchema;
import io.mapsmessaging.canbus.canaerospace.schema.CanaerospaceSchemaRegistry;
import io.mapsmessaging.canbus.canaerospace.schema.DataTypeEntry;
import io.mapsmessaging.canbus.canaerospace.schema.DataTypesDefinition;
import io.mapsmessaging.canbus.canaerospace.schema.IdRange;
import io.mapsmessaging.canbus.canaerospace.schema.IdentifierDefinition;
import io.mapsmessaging.canbus.canaerospace.schema.IdentifierRangeDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

class CanaerospaceSchemaRoundTripParserTest {

  private static final Gson GSON = new Gson();

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
    AtomicInteger roundTripped = new AtomicInteger(0);

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
      JsonObject json = parsePayloadToJson(identifier.id, payload);
      Assertions.assertNotNull(json, "Parser returned null JSON for id=" + identifier.id);

      executed.incrementAndGet();

      validateJsonBasics(json, identifier);

      if (tryValidateNumericValue(json, identifier)) {
        validated.incrementAndGet();
      }

      byte[] rebuiltPayload = encodePayloadFromJson(json);
      Assertions.assertArrayEquals(
          payload,
          rebuiltPayload,
          "Payload round trip failed for id=" + identifier.id + " name=" + identifier.name
      );
      roundTripped.incrementAndGet();
    }

    Assertions.assertTrue(executed.get() > 0, "No packets were executed through the parser");
    Assertions.assertEquals(executed.get(), roundTripped.get(), "Not all executed packets round-tripped");
    System.out.println(
        "Executed packets: " + executed.get()
            + ", numeric validations: " + validated.get()
            + ", round trips: " + roundTripped.get()
    );
  }

  /**
   * Expected payload layout (DLC=8):
   * [0]=nodeId, [1]=dataType, [2]=serviceCode, [3]=messageCode, [4..7]=data
   */
  private static JsonObject parsePayloadToJson(int canId, byte[] payload) throws Exception {
    if (frameParser == null) {
      CanaerospaceSchemaRegistry registry = CanaerospaceSchemaRegistry.loadFromClasspath();
      frameParser = new CanaerospaceFrameParser(registry);
    }

    ParsedCanaerospaceMessage parsed = frameParser.parse(canId, payload);

    JsonObject json = new JsonObject();
    json.addProperty("canId", parsed.getCanId());
    addString(json, "messageType", parsed.getMessageType());

    json.addProperty("nodeId", parsed.getNodeId());
    json.addProperty("payloadDataTypeNumber", parsed.getPayloadDataTypeNumber());
    addString(json, "payloadDataTypeName", parsed.getPayloadDataTypeName());
    json.addProperty("serviceCode", parsed.getServiceCode());
    json.addProperty("messageCode", parsed.getMessageCode());

    addString(json, "group", parsed.getGroup());
    addString(json, "title", parsed.getTitle());
    addString(json, "name", parsed.getName());
    addString(json, "schemaDataType", parsed.getSchemaDataType());
    addString(json, "units", parsed.getUnits());
    addNumber(json, "resolution", parsed.getResolution());
    addString(json, "notes", parsed.getNotes());
    addNumber(json, "rangeMin", parsed.getRangeMin());
    addNumber(json, "rangeMax", parsed.getRangeMax());

    Object raw = parsed.getRawValue();
    if (raw != null) {
      json.add("rawValue", GSON.toJsonTree(raw));
    }

    if (parsed.getEngineeringValue() != null) {
      json.addProperty("engineeringValue", parsed.getEngineeringValue());
    }

    json.addProperty("dataTypeMismatch", parsed.isDataTypeMismatch());

    return json;
  }

  private static byte[] encodePayloadFromJson(JsonObject json) {
    int nodeId = requireInt(json, "nodeId");
    int payloadDataTypeNumber = requireInt(json, "payloadDataTypeNumber");
    int serviceCode = requireInt(json, "serviceCode");
    int messageCode = requireInt(json, "messageCode");

    String schemaDataType = getString(json, "schemaDataType");
    if (schemaDataType == null || schemaDataType.isBlank()) {
      schemaDataType = getString(json, "payloadDataTypeName");
    }

    Assertions.assertNotNull(schemaDataType, "Missing schemaDataType/payloadDataTypeName in JSON");

    Object rawValue = null;
    JsonElement rawElement = json.get("rawValue");
    if (rawElement != null && !rawElement.isJsonNull()) {
      rawValue = GSON.fromJson(rawElement, Object.class);
    }

    if (rawValue == null && json.has("engineeringValue")) {
      rawValue = json.get("engineeringValue").getAsDouble();
    }

    byte[] dataBytes = DataTypeCodec.encode(schemaDataType, rawValue);

    byte[] payload = new byte[8];
    payload[0] = (byte) nodeId;
    payload[1] = (byte) payloadDataTypeNumber;
    payload[2] = (byte) serviceCode;
    payload[3] = (byte) messageCode;
    System.arraycopy(dataBytes, 0, payload, 4, 4);

    return payload;
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
        Assertions.assertTrue(
            raw >= Short.MIN_VALUE && raw <= Short.MAX_VALUE,
            "SHORT raw overflow for raw=" + raw + " value=" + chosenEngineeringValue + " res=" + resolution
        );
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
        float floatValue = (float) chosenEngineeringValue;
        putFloat32BigEndian(out, floatValue);
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
        out[0] = 42;
        out[1] = 0;
        out[2] = 0;
        out[3] = 0;
      }
      case "CHAR2", "UCHAR2", "ACHAR2" -> {
        out[0] = 1;
        out[1] = 2;
        out[2] = 0;
        out[3] = 0;
      }
      case "CHAR4", "UCHAR4" -> {
        out[0] = 1;
        out[1] = 2;
        out[2] = 3;
        out[3] = 4;
      }
      case "ACHAR4" -> {
        out[0] = 'A';
        out[1] = 'B';
        out[2] = 'C';
        out[3] = 'D';
      }
      case "SHORT2" -> {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 10);
        buffer.putShort((short) 20);
        byte[] bytes = buffer.array();
        System.arraycopy(bytes, 0, out, 0, 4);
      }
      case "USHORT2" -> {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 10);
        buffer.putShort((short) 20);
        byte[] bytes = buffer.array();
        System.arraycopy(bytes, 0, out, 0, 4);
      }
      case "BSHORT" -> {
        out[0] = 0x12;
        out[1] = 0x34;
        out[2] = 0;
        out[3] = 0;
      }
      case "BLONG", "MEMID", "CHKSUM" -> {
        out[0] = 0x12;
        out[1] = 0x34;
        out[2] = 0x56;
        out[3] = 0x78;
      }
      case "NODATA" -> {
        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        out[3] = 0;
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

    long minimumRaw;
    long maximumRaw;

    switch (dataTypeName) {
      case "SHORT" -> {
        minimumRaw = Short.MIN_VALUE;
        maximumRaw = Short.MAX_VALUE;
      }
      case "USHORT", "BSHORT" -> {
        minimumRaw = 0;
        maximumRaw = 0xFFFFL;
      }
      case "VARIABLE3" -> {
        minimumRaw = -(1L << 23);
        maximumRaw = (1L << 23) - 1;
      }
      case "UVARIABLE3" -> {
        minimumRaw = 0;
        maximumRaw = (1L << 24) - 1;
      }
      case "LONG" -> {
        minimumRaw = Integer.MIN_VALUE;
        maximumRaw = Integer.MAX_VALUE;
      }
      case "ULONG", "BLONG", "MEMID", "CHKSUM" -> {
        minimumRaw = 0;
        maximumRaw = 0xFFFFFFFFL;
      }
      default -> {
        return engineeringValue;
      }
    }

    double minimumEngineering = minimumRaw * resolution;
    double maximumEngineering = maximumRaw * resolution;

    if (engineeringValue < minimumEngineering) {
      return minimumEngineering;
    }
    if (engineeringValue > maximumEngineering) {
      return maximumEngineering;
    }
    return engineeringValue;
  }

  private static double chooseEngineeringValue(Double minimum, Double maximum) {
    if (minimum == null && maximum == null) {
      return 1.0;
    }
    if (minimum != null && maximum != null) {
      return (minimum + maximum) / 2.0;
    }
    if (minimum != null) {
      return minimum;
    }
    return maximum;
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
      Assertions.assertEquals(identifier.name, json.get("name").getAsString(), "JSON name mismatch on " + identifier.id);
    }
  }

  private static boolean tryValidateNumericValue(JsonObject json, ExpandedIdentifier identifier) {
    if (identifier.resolution == null) {
      return false;
    }

    JsonElement valueElement = json.get("engineeringValue");
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
    List<IdentifierRangeDefinition> list = Optional.ofNullable(schema.getIdentifierRanges())
        .orElseGet(Collections::emptyList);

    List<ExpandedIdentifier> expanded = new ArrayList<>();
    for (IdentifierRangeDefinition rangeDefinition : list) {
      IdRange idRange = rangeDefinition.getIdRange();
      if (idRange == null || idRange.getMin() == null || idRange.getMax() == null) {
        continue;
      }

      int minimum = idRange.getMin().intValue();
      int maximum = idRange.getMax().intValue();

      Double rangeMinimum = rangeDefinition.getRange() == null || rangeDefinition.getRange().getMin() == null
          ? null
          : rangeDefinition.getRange().getMin().doubleValue();

      Double rangeMaximum = rangeDefinition.getRange() == null || rangeDefinition.getRange().getMax() == null
          ? null
          : rangeDefinition.getRange().getMax().doubleValue();

      Double resolution = rangeDefinition.getResolution();

      for (int id = minimum; id <= maximum; id++) {
        ExpandedIdentifier expandedIdentifier = ExpandedIdentifier.fromRange(
            rangeDefinition,
            id,
            (id - minimum) + 1,
            rangeMinimum,
            rangeMaximum,
            resolution
        );
        expanded.add(expandedIdentifier);
      }
    }
    return expanded;
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

  private static int requireInt(JsonObject object, String field) {
    JsonElement element = object.get(field);
    Assertions.assertNotNull(element, "Missing field '" + field + "'");
    Assertions.assertFalse(element.isJsonNull(), "Field '" + field + "' is null");
    return element.getAsInt();
  }

  private static String getString(JsonObject object, String field) {
    JsonElement element = object.get(field);
    if (element == null || element.isJsonNull()) {
      return null;
    }
    return element.getAsString();
  }

  private static void addString(JsonObject jsonObject, String field, String value) {
    if (value != null) {
      jsonObject.addProperty(field, value);
    }
  }

  private static void addNumber(JsonObject jsonObject, String field, Number value) {
    if (value != null) {
      jsonObject.addProperty(field, value);
    }
  }

  private static final class ExpandedIdentifier {

    private final int id;
    private final String name;
    private final String dataTypeName;
    private final Double rangeMin;
    private final Double rangeMax;
    private final Double resolution;

    private ExpandedIdentifier(
        int id,
        String name,
        String dataTypeName,
        Double rangeMin,
        Double rangeMax,
        Double resolution
    ) {
      this.id = id;
      this.name = name;
      this.dataTypeName = dataTypeName;
      this.rangeMin = rangeMin;
      this.rangeMax = rangeMax;
      this.resolution = resolution;
    }

    static ExpandedIdentifier fromConcrete(IdentifierDefinition identifier) {
      Double minimum = identifier.getRange() == null || identifier.getRange().getMin() == null
          ? null
          : identifier.getRange().getMin().doubleValue();

      Double maximum = identifier.getRange() == null || identifier.getRange().getMax() == null
          ? null
          : identifier.getRange().getMax().doubleValue();

      return new ExpandedIdentifier(
          identifier.getId(),
          identifier.getName(),
          identifier.getDataType(),
          minimum,
          maximum,
          identifier.getResolution()
      );
    }

    static ExpandedIdentifier fromRange(
        IdentifierRangeDefinition rangeDefinition,
        int id,
        int ordinal,
        Double rangeMin,
        Double rangeMax,
        Double resolution
    ) {
      String template = rangeDefinition.getTitleTemplate();
      String name = template == null ? null : template.replace("{n}", String.valueOf(ordinal));

      return new ExpandedIdentifier(
          id,
          name,
          rangeDefinition.getDataType(),
          rangeMin,
          rangeMax,
          resolution
      );
    }
  }
}