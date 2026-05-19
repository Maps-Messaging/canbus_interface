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

import java.util.Arrays;
import java.util.Optional;

public class CanaerospaceFrameParser {

  private final CanaerospaceSchemaRegistry registry;

  public CanaerospaceFrameParser(CanaerospaceSchemaRegistry registry) {
    if (registry == null) {
      throw new IllegalArgumentException("registry must not be null");
    }
    this.registry = registry;
  }

   public ParsedCanaerospaceMessage parse(int canId, byte[] payload) {
    if (payload == null) {
      throw new IllegalArgumentException("payload must not be null");
    }
    if (payload.length != 8) {
      throw new IllegalArgumentException("payload must be 8 bytes (DLC=8), was " + payload.length);
    }

    int nodeId = payload[0] & 0xFF;
    int payloadDataTypeNumber = payload[1] & 0xFF;
    int serviceCode = payload[2] & 0xFF;
    int messageCode = payload[3] & 0xFF;
    byte[] dataBytes = Arrays.copyOfRange(payload, 4, 8);

    Optional<MessageTypeDefinition> messageTypeOptional = registry.findMessageType(canId);
    Optional<IdentifierDefinition> identifierOptional = registry.findIdentifier(canId);

    ResolvedIdentifier resolvedIdentifier = null;
    if (identifierOptional.isPresent()) {
      resolvedIdentifier = ResolvedIdentifier.fromExact(identifierOptional.get());
    }

    Object raw = null;
    Double engineeringValue = null;
    String expectedDataTypeName = null;

    if (resolvedIdentifier != null && resolvedIdentifier.getDataType() != null) {
      expectedDataTypeName = resolvedIdentifier.getDataType();

      raw = DataTypeCodec.decode(expectedDataTypeName, dataBytes);

      Double resolution = resolvedIdentifier.getResolution();
      if (resolution != null && raw instanceof Number number) {
        engineeringValue = number.doubleValue() * resolution;
      } else if (raw instanceof Float floatValue) {
        engineeringValue = floatValue.doubleValue();
      } else if (raw instanceof Double doubleValue) {
        engineeringValue =doubleValue;
      }
    }

    Optional<String> payloadDataTypeNameOptional = registry.findDataTypeNameByNumber(payloadDataTypeNumber);
    String payloadDataTypeName = payloadDataTypeNameOptional.orElse(null);

    boolean dataTypeMismatch = false;
    if (expectedDataTypeName != null && payloadDataTypeName != null) {
      dataTypeMismatch = !expectedDataTypeName.equals(payloadDataTypeName);
    }

    ParsedCanaerospaceMessage parsed = new ParsedCanaerospaceMessage();
    parsed.setCanId(canId);
    parsed.setMessageType(messageTypeOptional.map(MessageTypeDefinition::getName).orElse(null));

    parsed.setNodeId(nodeId);
    parsed.setPayloadDataTypeNumber(payloadDataTypeNumber);
    parsed.setPayloadDataTypeName(payloadDataTypeName);
    parsed.setServiceCode(serviceCode);
    parsed.setMessageCode(messageCode);
    parsed.setDataBytes(dataBytes);

    if (resolvedIdentifier != null) {
      parsed.setGroup(resolvedIdentifier.getGroup());
      parsed.setTitle(resolvedIdentifier.getTitle());
      parsed.setName(resolvedIdentifier.getName());
      parsed.setSchemaDataType(resolvedIdentifier.getDataType());
      parsed.setUnits(resolvedIdentifier.getUnits());
      parsed.setResolution(resolvedIdentifier.getResolution());
      parsed.setNotes(resolvedIdentifier.getNotes());
      parsed.setRangeMin(resolvedIdentifier.getRangeMin());
      parsed.setRangeMax(resolvedIdentifier.getRangeMax());
    }

    parsed.setRawValue(raw);
    parsed.setEngineeringValue(engineeringValue);
    parsed.setDataTypeMismatch(dataTypeMismatch);

    return parsed;
  }
}