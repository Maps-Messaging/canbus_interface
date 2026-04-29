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

package io.mapsmessaging.canbus.canaerospace.parser;

import io.mapsmessaging.canbus.canaerospace.schema.IdentifierDefinition;
import io.mapsmessaging.canbus.canaerospace.schema.IdentifierRangeDefinition;
import lombok.Getter;

@Getter
public class ResolvedIdentifier {

  private final Integer canId;
  private final String group;
  private final String title;
  private final String name;
  private final String dataType;
  private final String units;
  private final Double resolution;
  private final String notes;
  private final Double rangeMin;
  private final Double rangeMax;

  public ResolvedIdentifier(Integer canId,
                             String group,
                             String title,
                             String name,
                             String dataType,
                             String units,
                             Double resolution,
                             String notes,
                             Double rangeMin,
                             Double rangeMax) {
    this.canId = canId;
    this.group = group;
    this.title = title;
    this.name = name;
    this.dataType = dataType;
    this.units = units;
    this.resolution = resolution;
    this.notes = notes;
    this.rangeMin = rangeMin;
    this.rangeMax = rangeMax;
  }

  public static ResolvedIdentifier fromExact(IdentifierDefinition definition) {
    Double min = null;
    Double max = null;
    if (definition.getRange() != null) {
      if (definition.getRange().getMin() != null) {
        min = definition.getRange().getMin().doubleValue();
      }
      if (definition.getRange().getMax() != null) {
        max = definition.getRange().getMax().doubleValue();
      }
    }

    Double resolution = definition.getResolution();

    return new ResolvedIdentifier(
        definition.getId(),
        definition.getGroup(),
        definition.getTitle(),
        definition.getName(),
        definition.getDataType(),
        definition.getUnits(),
        resolution,
        definition.getNotes(),
        min,
        max
    );
  }

  public static ResolvedIdentifier fromRange(int canId, IdentifierRangeDefinition range) {
    int base = range.getIdRange().getMin();
    int n = (canId - base) + 1;

    String title = range.getTitleTemplate();
    if (title != null) {
      title = title.replace("#{n}", Integer.toString(n));
    }

    Double min = null;
    Double max = null;
    if (range.getRange() != null) {
      if (range.getRange().getMin() != null) {
        min = range.getRange().getMin().doubleValue();
      }
      if (range.getRange().getMax() != null) {
        max = range.getRange().getMax().doubleValue();
      }
    }

    return new ResolvedIdentifier(
        canId,
        range.getGroup(),
        title,
        null,
        range.getDataType(),
        range.getUnits(),
        range.getResolution(),
        range.getNotes(),
        min,
        max
    );
  }
}