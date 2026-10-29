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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Immutable, protocol-specific CANboat schema. A PGN can retain multiple conditional layouts. */
public final class CanboatDatabase {
  private final String protocol;
  private final String version;
  private final String schemaVersion;
  private final List<CanboatMessage> messages;
  private final Map<Integer, List<CanboatMessage>> variants;
  private final Map<String, Map<String, Map<String, String>>> lookups;
  private static final Set<String> TYPES =
      Set.of(
          "NUMBER",
          "FLOAT",
          "LOOKUP",
          "INDIRECT_LOOKUP",
          "BITLOOKUP",
          "BINARY",
          "PGN",
          "ADDRESS",
          "DURATION",
          "FIELD_INDEX",
          "ISO_NAME",
          "DATE",
          "TIME",
          "MMSI",
          "DECIMAL",
          "DYNAMIC_FIELD_KEY",
          "DYNAMIC_FIELD_VALUE",
          "DYNAMIC_FIELD_LENGTH",
          "VARIABLE",
          "RESERVED",
          "SPARE",
          "STRING_FIX",
          "STRING_LAU",
          "STRING_LZ");

  private CanboatDatabase(Element root) {
    Map<String, String> metadata = properties(root);
    protocol = metadata.getOrDefault("Protocol", "nmea2000");
    version = metadata.getOrDefault("Version", "unknown");
    schemaVersion = metadata.get("SchemaVersion");
    if (schemaVersion == null || !schemaVersion.startsWith("2.")) {
      throw new IllegalArgumentException("Unsupported CANboat schema version: " + schemaVersion);
    }
    if (!protocol.equals("nmea2000") && !protocol.equals("j1939")) {
      throw new IllegalArgumentException("Unsupported CANboat protocol: " + protocol);
    }
    List<CanboatMessage> all = new ArrayList<>();
    Map<Integer, List<CanboatMessage>> grouped = new LinkedHashMap<>();
    Element pgns = child(root, "PGNs");
    if (pgns == null) {
      throw new IllegalArgumentException("Missing CANboat PGNs");
    }
    for (Element element : children(pgns, "PGNInfo")) {
      List<CanboatField> fields = new ArrayList<>();
      Element container = child(element, "Fields");
      if (container != null) {
        for (Element entry : children(container, "Field")) {
          CanboatField field = new CanboatField(properties(entry));
          if (!TYPES.contains(field.type())) {
            throw new IllegalArgumentException("Unsupported CANboat field type: " + field.type());
          }
          fields.add(field);
        }
      }
      CanboatMessage message = new CanboatMessage(properties(element), fields);
      if (message.id() == null || message.pgn() < 0) {
        throw new IllegalArgumentException("Invalid CANboat message metadata");
      }
      all.add(message);
      grouped.computeIfAbsent(message.pgn(), ignored -> new ArrayList<>()).add(message);
    }
    messages = List.copyOf(all);
    Map<Integer, List<CanboatMessage>> immutable = new LinkedHashMap<>();
    grouped.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
    variants = Map.copyOf(immutable);
    Map<String, Map<String, Map<String, String>>> tables = new LinkedHashMap<>();
    for (String container :
        List.of(
            "LookupEnumerations",
            "LookupIndirectEnumerations",
            "LookupBitEnumerations",
            "LookupFieldTypeEnumerations")) {
      Element list = child(root, container);
      if (list == null) {
        continue;
      }
      for (Element table : children(list, null)) {
        Map<String, Map<String, String>> entries = new LinkedHashMap<>();
        for (Element entry : children(table, null)) {
          Map<String, String> values = new LinkedHashMap<>(properties(entry));
          for (int index = 0; index < entry.getAttributes().getLength(); index++) {
            Node attr = entry.getAttributes().item(index);
            values.put(attr.getNodeName(), attr.getNodeValue());
          }
          String key =
              values.getOrDefault(
                  "Value", values.getOrDefault("Bit", values.getOrDefault("Value1", "")));
          if (values.containsKey("Value2")) {
            key += ":" + values.get("Value2");
          }
          entries.put(key, Map.copyOf(values));
        }
        tables.put(table.getAttribute("Name"), Map.copyOf(entries));
      }
    }
    lookups = Map.copyOf(tables);
  }

  public static CanboatDatabase parse(Element root) {
    return new CanboatDatabase(root);
  }

  public String getProtocol() {
    return protocol;
  }

  public String getVersion() {
    return version;
  }

  public String getSchemaVersion() {
    return schemaVersion;
  }

  public List<CanboatMessage> getMessages() {
    return messages;
  }

  public List<CanboatMessage> variants(int pgn) {
    return variants.getOrDefault(pgn, List.of());
  }

  public List<CanboatMessage> candidates(int pgn) {
    List<CanboatMessage> exact = variants(pgn);
    if (!exact.isEmpty()) {
      return exact;
    }
    CanboatMessage range = fallback(pgn);
    return range == null ? List.of() : List.of(range);
  }

  public CanboatMessage fallback(int pgn) {
    if (pgn < 0 || pgn > 0x3FFFF) {
      return null;
    }
    CanboatMessage range = null;
    for (CanboatMessage message : messages) {
      if (message.fallback()
          && message.pgn() <= pgn
          && (range == null || message.pgn() > range.pgn())) {
        range = message;
      }
    }
    return range;
  }

  public Map<String, String> lookup(String table, String key) {
    return lookups.getOrDefault(table, Map.of()).get(key);
  }

  public static Element child(Element parent, String name) {
    return children(parent, name).stream().findFirst().orElse(null);
  }

  private static List<Element> children(Element parent, String name) {
    List<Element> result = new ArrayList<>();
    for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof Element element && (name == null || element.getTagName().equals(name))) {
        result.add(element);
      }
    }
    return result;
  }

  private static Map<String, String> properties(Element parent) {
    Map<String, String> result = new LinkedHashMap<>();
    for (Element element : children(parent, null)) {
      if (children(element, null).isEmpty()) {
        result.put(element.getTagName(), element.getTextContent().trim());
      }
    }
    return result;
  }
}
