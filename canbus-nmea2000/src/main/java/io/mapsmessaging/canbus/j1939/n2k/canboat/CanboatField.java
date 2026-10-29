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

import java.math.BigInteger;
import java.util.Map;

/** Complete generated CANboat field metadata, including dynamic and discriminator attributes. */
public record CanboatField(Map<String, String> properties) {
  public CanboatField {
    properties = Map.copyOf(properties);
  }

  public String text(String key) {
    return properties.get(key);
  }

  public String text(String key, String fallback) {
    return properties.getOrDefault(key, fallback);
  }

  public int number(String key, int fallback) {
    String value = text(key);
    return value == null ? fallback : Integer.parseInt(value);
  }

  public BigInteger integer(String key) {
    String value = text(key);
    return value == null ? null : new BigInteger(value);
  }

  public int order() {
    return number("Order", 0);
  }

  public String id() {
    return text("Id", "field" + order());
  }

  public String type() {
    return text("FieldType", "BINARY");
  }
}
