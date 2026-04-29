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

import io.mapsmessaging.canbus.canaerospace.schema.CanaerospaceSchemaRegistry;
import io.mapsmessaging.canbus.canaerospace.schema.IdentifierDefinition;
import io.mapsmessaging.canbus.canaerospace.schema.MessageTypeDefinition;
import io.mapsmessaging.canbus.canaerospace.schema.NumericRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;

class CanaerospaceFrameParserTest {

  @Test
  void parsesFlightStateBodyLongitudinalAccelerationCanId300() {
    int canId = 0x12C;

    CanaerospaceSchemaRegistry registry = Mockito.mock(CanaerospaceSchemaRegistry.class);

    MessageTypeDefinition messageTypeDefinition = Mockito.mock(MessageTypeDefinition.class);
    Mockito.when(messageTypeDefinition.getName()).thenReturn("Flight State/Air Data");
    Mockito.when(registry.findMessageType(canId)).thenReturn(Optional.of(messageTypeDefinition));

    NumericRange numericRange = Mockito.mock(NumericRange.class);
    Mockito.when(numericRange.getMin()).thenReturn(BigDecimal.valueOf(-16));
    Mockito.when(numericRange.getMax()).thenReturn(BigDecimal.valueOf(16));

    IdentifierDefinition identifierDefinition = Mockito.mock(IdentifierDefinition.class);
    Mockito.when(identifierDefinition.getId()).thenReturn(canId);
    Mockito.when(identifierDefinition.getGroup()).thenReturn("Flight State/Air Data");
    Mockito.when(identifierDefinition.getTitle()).thenReturn("Body longitudinal acceleration");
    Mockito.when(identifierDefinition.getName()).thenReturn("Body longitudinal acceleration");
    Mockito.when(identifierDefinition.getDataType()).thenReturn("SHORT");
    Mockito.when(identifierDefinition.getUnits()).thenReturn("g");
    Mockito.when(identifierDefinition.getResolution()).thenReturn(0.000488d);
    Mockito.when(identifierDefinition.getNotes()).thenReturn("+ Forward");
    Mockito.when(identifierDefinition.getRange()).thenReturn(numericRange);

    Mockito.when(registry.findIdentifier(canId)).thenReturn(Optional.of(identifierDefinition));

    int payloadDataTypeNumber = 1;
    Mockito.when(registry.findDataTypeNameByNumber(payloadDataTypeNumber)).thenReturn(Optional.of("SHORT"));

    CanaerospaceFrameParser parser = new CanaerospaceFrameParser(registry);

    // CANaerospace payload layout (your parser):
    // [0]=nodeId, [1]=payloadDataTypeNumber, [2]=serviceCode, [3]=messageCode, [4..7]=data
    byte[] payload = new byte[] {
        (byte) 0x2A,                 // nodeId = 42
        (byte) payloadDataTypeNumber, // payload data type number
        (byte) 0x00,                 // serviceCode
        (byte) 0x00,                 // messageCode
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 // raw data = 0
    };

    ParsedCanaerospaceMessage parsed = parser.parse(canId, payload);

    Assertions.assertNotNull(parsed);

    Assertions.assertEquals(canId, parsed.getCanId());
    Assertions.assertEquals("Flight State/Air Data", parsed.getMessageType());

    Assertions.assertEquals(42, parsed.getNodeId());
    Assertions.assertEquals(payloadDataTypeNumber, parsed.getPayloadDataTypeNumber());
    Assertions.assertEquals("SHORT", parsed.getPayloadDataTypeName());
    Assertions.assertEquals(0, parsed.getServiceCode());
    Assertions.assertEquals(0, parsed.getMessageCode());
    Assertions.assertArrayEquals(new byte[] {0, 0, 0, 0}, parsed.getDataBytes());

    Assertions.assertEquals("Flight State/Air Data", parsed.getGroup());
    Assertions.assertEquals("Body longitudinal acceleration", parsed.getTitle());
    Assertions.assertEquals("Body longitudinal acceleration", parsed.getName());
    Assertions.assertEquals("SHORT", parsed.getSchemaDataType());
    Assertions.assertEquals("g", parsed.getUnits());
    Assertions.assertEquals(0.000488d, parsed.getResolution());
    Assertions.assertEquals("+ Forward", parsed.getNotes());
    Assertions.assertEquals(-16.0d, parsed.getRangeMin());
    Assertions.assertEquals(16.0d, parsed.getRangeMax());

    Assertions.assertFalse(parsed.isDataTypeMismatch());

    // With raw = 0, engineering value must be 0 no matter how SHORT decoding is implemented.
    Assertions.assertNotNull(parsed.getRawValue());
    Assertions.assertTrue(parsed.getRawValue() instanceof Number);
    Assertions.assertEquals(0.0d, parsed.getEngineeringValue());
  }
}