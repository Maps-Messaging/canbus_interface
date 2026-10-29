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

import java.util.List;
import java.util.Map;

public record CanboatMessage(Map<String, String> properties, List<CanboatField> fields) {
  public CanboatMessage {
    properties = Map.copyOf(properties);
    fields = List.copyOf(fields);
  }

  public int pgn() {
    return number("PGN", -1);
  }

  public String id() {
    return properties.get("Id");
  }

  public String type() {
    return properties.getOrDefault("Type", "Single");
  }

  public boolean fallback() {
    return Boolean.parseBoolean(properties.get("Fallback"));
  }

  public int number(String key, int fallback) {
    String value = properties.get(key);
    return value == null ? fallback : Integer.parseInt(value);
  }

  public int minimum() {
    return fallback() ? 0 : number("MinLength", number("Length", 0));
  }

  public int length() {
    return fallback() ? -1 : number("Length", -1);
  }

  public CanboatField field(int order) {
    return fields.stream().filter(f -> f.order() == order).findFirst().orElse(null);
  }
}
