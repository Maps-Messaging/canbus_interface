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
package io.mapsmessaging.canbus.canaerospace.parser;

import io.mapsmessaging.canbus.canaerospace.schema.IdRange;
import io.mapsmessaging.canbus.canaerospace.schema.IdentifierDefinition;
import io.mapsmessaging.canbus.canaerospace.schema.IdentifierRangeDefinition;
import io.mapsmessaging.canbus.canaerospace.schema.NumericRange;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResolvedIdentifierTest {

  @Test
  void exactIdentifierKeepsMetadataAndDecimalBounds() {
    IdentifierDefinition definition = new IdentifierDefinition();
    definition.setId(317);
    definition.setGroup("Navigation");
    definition.setTitle("Altitude");
    definition.setName("altitude");
    definition.setDataType("FLOAT");
    definition.setUnits("m");
    definition.setResolution(0.1);
    definition.setNotes("above datum");
    NumericRange bounds = new NumericRange();
    bounds.setMin(new BigDecimal("-12.5"));
    bounds.setMax(new BigDecimal("345.25"));
    definition.setRange(bounds);

    ResolvedIdentifier resolved = ResolvedIdentifier.fromExact(definition);

    assertEquals(317, resolved.getCanId());
    assertEquals("Navigation", resolved.getGroup());
    assertEquals("Altitude", resolved.getTitle());
    assertEquals("altitude", resolved.getName());
    assertEquals("FLOAT", resolved.getDataType());
    assertEquals("m", resolved.getUnits());
    assertEquals(0.1, resolved.getResolution());
    assertEquals("above datum", resolved.getNotes());
    assertEquals(-12.5, resolved.getRangeMin());
    assertEquals(345.25, resolved.getRangeMax());
  }

  @Test
  void exactIdentifierWithMissingBoundsDoesNotInventValues() {
    IdentifierDefinition definition = new IdentifierDefinition();
    definition.setId(318);
    NumericRange bounds = new NumericRange();
    bounds.setMin(new BigDecimal("0"));
    definition.setRange(bounds);

    ResolvedIdentifier resolved = ResolvedIdentifier.fromExact(definition);

    assertEquals(0.0, resolved.getRangeMin());
    assertNull(resolved.getRangeMax());
    assertNull(resolved.getResolution());
  }

  @Test
  void rangeIdentifierExpandsOrdinalAndKeepsMetadata() {
    IdentifierRangeDefinition definition = new IdentifierRangeDefinition();
    IdRange ids = new IdRange();
    ids.setMin(500);
    ids.setMax(503);
    definition.setIdRange(ids);
    definition.setTitleTemplate("Sensor #{n}");
    definition.setGroup("Engine");
    definition.setDataType("USHORT");
    definition.setUnits("rpm");
    definition.setResolution(1.0);
    NumericRange bounds = new NumericRange();
    bounds.setMax(new BigDecimal("6000"));
    definition.setRange(bounds);

    ResolvedIdentifier resolved = ResolvedIdentifier.fromRange(502, definition);

    assertEquals(502, resolved.getCanId());
    assertEquals("Sensor 3", resolved.getTitle());
    assertNull(resolved.getName());
    assertEquals("Engine", resolved.getGroup());
    assertEquals("USHORT", resolved.getDataType());
    assertEquals("rpm", resolved.getUnits());
    assertEquals(1.0, resolved.getResolution());
    assertNull(resolved.getRangeMin());
    assertEquals(6000.0, resolved.getRangeMax());
  }

  @Test
  void rangeIdentifierWithoutTemplateOrBoundsStaysUnlabelled() {
    IdentifierRangeDefinition definition = new IdentifierRangeDefinition();
    IdRange ids = new IdRange();
    ids.setMin(700);
    ids.setMax(701);
    definition.setIdRange(ids);

    ResolvedIdentifier resolved = ResolvedIdentifier.fromRange(700, definition);

    assertNull(resolved.getTitle());
    assertNull(resolved.getRangeMin());
    assertNull(resolved.getRangeMax());
  }
}
