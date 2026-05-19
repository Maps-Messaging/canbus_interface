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

package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Getter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CanaerospaceSchemaRegistry {

  public static final String DEFAULT_SCHEMA_RESOURCE = "canaerospace_schema.yaml";
  private static final int MIN_CANAEROSPACE_IDENTIFIER = 0;
  private static final int MAX_CANAEROSPACE_IDENTIFIER = 0x7FF; // ( max 11 bits)
  private static final int MAX_IDENTIFIER_RANGE_EXPANSION = MAX_CANAEROSPACE_IDENTIFIER + 1;

  @Getter
  private final CanaerospaceSchema schema;

  private final Map<Integer, IdentifierDefinition> identifiersById;
  private final Map<Integer, String> dataTypeNameByNumber;
  private final List<MessageTypeDefinition> messageTypes;

  public CanaerospaceSchemaRegistry(CanaerospaceSchema schema) {
    if (schema == null) {
      throw new IllegalArgumentException("schema must not be null");
    }
    this.schema = schema;
    List<IdentifierRangeDefinition> identifierRanges = buildIdentifierRanges(schema);
    this.identifiersById = buildIdentifiersById(schema, identifierRanges);
    this.dataTypeNameByNumber = buildDataTypeNameByNumber(schema);
    this.messageTypes = buildMessageTypes(schema);
  }

  public Optional<MessageTypeDefinition> findMessageType(int canId) {
    for (MessageTypeDefinition messageType : messageTypes) {
      IdRange idRange = messageType.getIdRange();
      if (idRange != null) {
        Integer min = idRange.getMin();
        Integer max = idRange.getMax();
        if (min != null && max != null && canId >= min && canId <= max) {
          return Optional.of(messageType);
        }
      }
    }
    return Optional.empty();
  }

  public Optional<IdentifierDefinition> findIdentifier(int canId) {
    return Optional.ofNullable(identifiersById.get(canId));
  }

  public Optional<String> findDataTypeNameByNumber(int payloadDataTypeNumber) {
    return Optional.ofNullable(dataTypeNameByNumber.get(payloadDataTypeNumber));
  }

  public static CanaerospaceSchemaRegistry load(Path yamlPath) throws Exception {
    if (yamlPath == null) {
      throw new IllegalArgumentException("yamlPath must not be null");
    }
    if (!Files.exists(yamlPath)) {
      throw new IllegalArgumentException("YAML file not found: " + yamlPath);
    }

    try (InputStream inputStream = Files.newInputStream(yamlPath)) {
      CanaerospaceSchema loadedSchema = loadFromStream(inputStream);
      return new CanaerospaceSchemaRegistry(loadedSchema);
    }
  }

  public static CanaerospaceSchemaRegistry loadFromClasspath() throws Exception {
    return loadFromClasspath(DEFAULT_SCHEMA_RESOURCE);
  }

  public static CanaerospaceSchemaRegistry loadFromClasspath(String resourceName) throws Exception {
    if (resourceName == null || resourceName.isBlank()) {
      throw new IllegalArgumentException("resourceName must not be blank");
    }

    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    try (InputStream inputStream = classLoader.getResourceAsStream(resourceName)) {
      if (inputStream == null) {
        throw new IllegalArgumentException("Classpath resource not found: " + resourceName);
      }
      CanaerospaceSchema loadedSchema = loadFromStream(inputStream);
      return new CanaerospaceSchemaRegistry(loadedSchema);
    }
  }

  private static CanaerospaceSchema loadFromStream(InputStream inputStream) {
    LoaderOptions options = new LoaderOptions();
    Constructor constructor = new Constructor(CanaerospaceSchema.class, options);
    Yaml yaml = new Yaml(constructor);

    CanaerospaceSchema loadedSchema = yaml.load(inputStream);
    if (loadedSchema == null) {
      throw new IllegalStateException("YAML loaded null schema");
    }
    return loadedSchema;
  }

  private static Map<Integer, IdentifierDefinition> buildIdentifiersById(CanaerospaceSchema schema, List<IdentifierRangeDefinition> sortedRanges) {
    Map<Integer, IdentifierDefinition> result = new LinkedHashMap<>();

    List<IdentifierDefinition> identifiers = schema.getIdentifiers();
    if (identifiers != null && !identifiers.isEmpty()) {

      Map<Integer, List<IdentifierDefinition>> grouped = new LinkedHashMap<>();
      for (IdentifierDefinition identifier : identifiers) {
        Integer id = extractIdentifierId(identifier);
        if (id == null) {
          continue;
        }
        grouped.computeIfAbsent(id, ignored -> new ArrayList<>()).add(identifier);
      }

      for (Map.Entry<Integer, List<IdentifierDefinition>> entry : grouped.entrySet()) {
        Integer id = entry.getKey();
        List<IdentifierDefinition> values = entry.getValue();
        if (values.size() == 1) {
          result.put(id, values.getFirst());
        } else {
          IdentifierDefinition best = values.getFirst();
          for (IdentifierDefinition candidate : values) {
            if (isBetterIdentifier(candidate, best)) {
              best = candidate;
            }
          }
          result.put(id, best);
        }
      }
    }

    if (sortedRanges != null && !sortedRanges.isEmpty()) {
      for (IdentifierRangeDefinition range : sortedRanges) {
        IdRange idRange = range.getIdRange();
        if (idRange == null || idRange.getMin() == null || idRange.getMax() == null) {
          continue;
        }

        int min = idRange.getMin();
        int max = idRange.getMax();

        if (min < MIN_CANAEROSPACE_IDENTIFIER || max > MAX_CANAEROSPACE_IDENTIFIER || max < min) {
          throw new IllegalArgumentException(
              "Invalid CANaerospace identifier range: "
                  + min
                  + " to "
                  + max
                  + ". Expected range within 0 to "
                  + MAX_CANAEROSPACE_IDENTIFIER);
        }

        for (int canId = min; canId <= max; canId++) {
          if (!result.containsKey(canId)) {
            IdentifierDefinition expanded = expandRangeIdentifier(range, canId);
            result.put(canId, expanded);
          }
        }
      }
    }

    return Collections.unmodifiableMap(result);
  }

  private static IdentifierDefinition expandRangeIdentifier(IdentifierRangeDefinition range, int canId) {
    IdentifierDefinition expanded = new IdentifierDefinition();

    expanded.setId(canId);
    expanded.setGroup(range.getGroup());

    String title = range.getTitleTemplate();
    if (title != null) {
      int base = range.getIdRange().getMin();
      int n = (canId - base) + 1;
      title = title.replace("{n}", Integer.toString(n));
    }
    expanded.setTitle(title);
    expanded.setName(title);

    expanded.setDataType(range.getDataType());
    expanded.setUnits(range.getUnits());
    expanded.setResolution(range.getResolution());
    expanded.setNotes(range.getNotes());

    NumericRange rangeValue = range.getRange();
    expanded.setRange(rangeValue);

    expanded.setMessageType(null);
    expanded.setHex(null);

    return expanded;
  }

  private static boolean isBetterIdentifier(IdentifierDefinition candidate, IdentifierDefinition currentBest) {
    String candidateMessageType = safeString(candidate.getMessageType());
    String currentMessageType = safeString(currentBest.getMessageType());
    if (currentMessageType.isBlank() && !candidateMessageType.isBlank()) {
      return true;
    }

    String candidateDataType = safeString(candidate.getDataType());
    String currentDataType = safeString(currentBest.getDataType());
    if (currentDataType.isBlank() && !candidateDataType.isBlank()) {
      return true;
    }

    String candidateTitle = safeString(candidate.getTitle());
    String currentTitle = safeString(currentBest.getTitle());
    return currentTitle.isBlank() && !candidateTitle.isBlank();
  }

  private static String safeString(String value) {
    return value == null ? "" : value;
  }

  private static List<IdentifierRangeDefinition> buildIdentifierRanges(CanaerospaceSchema schema) {
    List<IdentifierRangeDefinition> ranges = schema.getIdentifierRanges();
    if (ranges == null || ranges.isEmpty()) {
      return List.of();
    }

    List<IdentifierRangeDefinition> sorted = new ArrayList<>(ranges);
    sorted.sort(Comparator.comparingInt(range -> {
      IdRange idRange = range.getIdRange();
      if (idRange == null || idRange.getMin() == null) {
        return Integer.MIN_VALUE;
      }
      return idRange.getMin();
    }));

    return Collections.unmodifiableList(sorted);
  }

  private static Map<Integer, String> buildDataTypeNameByNumber(CanaerospaceSchema schema) {
    DataTypesDefinition dataTypes = schema.getDataTypes();
    if (dataTypes == null) {
      return Map.of();
    }

    Map<Integer, String> byNumber = dataTypes.getByNumber();
    if (byNumber == null || byNumber.isEmpty()) {
      return Map.of();
    }

    return Collections.unmodifiableMap(new LinkedHashMap<>(byNumber));
  }

  private static List<MessageTypeDefinition> buildMessageTypes(CanaerospaceSchema schema) {
    List<MessageTypeDefinition> messageTypes = schema.getMessageTypes();
    if (messageTypes == null || messageTypes.isEmpty()) {
      return List.of();
    }

    List<MessageTypeDefinition> sorted = new ArrayList<>(messageTypes);
    sorted.sort(Comparator.comparingInt(messageTypeDefinition -> {
      IdRange idRange = messageTypeDefinition.getIdRange();
      if (idRange == null || idRange.getMin() == null) {
        return Integer.MIN_VALUE;
      }
      return idRange.getMin();
    }));

    return Collections.unmodifiableList(sorted);
  }

  private static Integer extractIdentifierId(IdentifierDefinition identifierDefinition) {
    return identifierDefinition.getId();
  }
}