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

package io.mapsmessaging.canbus.j1939.n2k.compile;

import io.mapsmessaging.canbus.j1939.n2k.model.N2kFieldDefinition;
import io.mapsmessaging.canbus.j1939.n2k.model.N2kFieldType;
import io.mapsmessaging.canbus.j1939.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.canbus.j1939.n2k.model.N2kMessageLengthType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class N2kCompiler {

  public static N2kCompiledRegistry compile(List<N2kMessageDefinition> messageDefinitions) {
    Map<Integer, N2kCompiledMessage> messagesByPgn = HashMap.newHashMap(messageDefinitions.size());

    for (N2kMessageDefinition messageDefinition : messageDefinitions) {
      N2kCompiledMessage compiledMessage = compileMessage(messageDefinition);
      messagesByPgn.put(compiledMessage.getPgn(), compiledMessage);
    }

    return new N2kCompiledRegistry(Map.copyOf(messagesByPgn));
  }

  private static int computeMinimumLengthBytes(List<N2kCompiledField> compiledFields) {
    int maxBitExclusive = 0;
    for (N2kCompiledField field : compiledFields) {
      if (!field.isCompileTimeFixed() || field.getBitLength() <= 0 ){
        continue;
      }
      int endBit = field.getBitOffset() +  field.getBitLength();
      if (endBit > maxBitExclusive) {
        maxBitExclusive = endBit;
      }
    }
    return (maxBitExclusive + 7) >>> 3;
  }

  private static N2kCompiledMessage compileMessage(N2kMessageDefinition messageDefinition) {
    List<N2kCompiledField> compiledFields = new ArrayList<>();
    HashSet<String> seenIds = new HashSet<>();
    boolean isPositionFixed = true;

    for (N2kFieldDefinition fieldDefinition : messageDefinition.getFields()) {
      isPositionFixed = computeField(fieldDefinition, seenIds, isPositionFixed, compiledFields);
    }

    int minimumLengthBytes = computeMinimumLengthBytes(compiledFields);

    if (messageDefinition.getLengthType() == N2kMessageLengthType.FIXED) {
      Integer fixedLengthBytes = messageDefinition.getFixedLengthBytes();
      if (fixedLengthBytes == null) {
        throw new IllegalArgumentException(
            "FIXED lengthType but fixedLengthBytes is null for PGN " + messageDefinition.getPgn()
        );
      }
      if (fixedLengthBytes < minimumLengthBytes) {
        throw new IllegalArgumentException(
            "Declared lengthBytes " + fixedLengthBytes + " is smaller than minimum " + minimumLengthBytes +
                " for PGN " + messageDefinition.getPgn()
        );
      }
    }
    return N2kCompiledMessage.builder()
        .pgn(messageDefinition.getPgn())
        .id(messageDefinition.getId())
        .description(messageDefinition.getDescription())
        .lengthType(messageDefinition.getLengthType())
        .fixedLengthBytes(messageDefinition.getFixedLengthBytes())
        .minimumLengthBytes(minimumLengthBytes)
        .fields(compiledFields)
        .definitions(messageDefinition.getFields())
        .build();
  }

  private boolean computeField(N2kFieldDefinition fieldDefinition,  HashSet<String> seenIds, boolean isPositionFixed, List<N2kCompiledField> compiledFields){
    N2kFieldType fieldType = fieldDefinition.getFieldType();
    boolean reserved = fieldType == N2kFieldType.RESERVED;

    String id = fieldDefinition.getId();
    if (!reserved && (id == null || id.isBlank() ||!seenIds.add(id))) {
      return isPositionFixed;
    }
    if (fieldType == N2kFieldType.STRING_LAU) {
      isPositionFixed = false;
    }
    boolean compileTimeFixed = isPositionFixed;

    N2kCompiledField.N2kCompiledFieldBuilder builder =
        N2kCompiledField.builder()
            .id(fieldDefinition.getId())
            .compileTimeFixed(compileTimeFixed)
            .name(fieldDefinition.getName())
            .signed(fieldDefinition.isSigned())
            .resolution(fieldDefinition.getResolution())
            .offset(fieldDefinition.getOffset())
            .rangeMin(fieldDefinition.getRangeMin())
            .rangeMax(fieldDefinition.getRangeMax())
            .unit(fieldDefinition.getUnit())
            .fieldType(fieldType)
            .reserved(reserved);

    if (supportsBitStorage(fieldType)) {
      Integer bitLength = resolveBitLength(fieldDefinition);
      if (bitLength != null && bitLength > 0) {
        int bytesToRead = computeBytesToRead(bitLength);

        builder
            .bitLength(bitLength)
            .bytesToRead(bytesToRead)
            .mask(computeMask(bitLength))
            .rawMin(computeRawMin(fieldDefinition.isSigned(), bitLength))
            .rawMax(computeRawMax(fieldDefinition.isSigned(), bitLength));
      }
    }

    if (compileTimeFixed
        && fieldDefinition.getBitOffset() != null
        && supportsBitStorage(fieldType)) {

      int bitOffset = fieldDefinition.getBitOffset();
      int startByte = bitOffset >>> 3;
      int startBit = bitOffset & 7;

      if ( builder.build().getBitLength() > 0) {
        int totalBits = startBit + builder.build().getBitLength();
        int bytesToRead = (totalBits + 7) >>> 3;

        builder
            .bitOffset(bitOffset)
            .startByte(startByte)
            .startBit(startBit)
            .bytesToRead(bytesToRead);
      }
    }

    compiledFields.add(builder.build());
    return isPositionFixed;
  }

  private static boolean supportsBitStorage(N2kFieldType fieldType) {
    return fieldType != N2kFieldType.STRING_LAU
        && fieldType != N2kFieldType.REPEAT_MARKER;
  }

  private static Integer resolveBitLength(N2kFieldDefinition fieldDefinition) {
    Integer bitLength = fieldDefinition.getBitLength();
    if (bitLength != null) {
      return bitLength;
    }

    String typeInPdf = fieldDefinition.getTypeInPdf();
    if (typeInPdf == null) {
      return null;
    }

    return switch (typeInPdf.trim().toLowerCase()) {
      case "int8", "uint8", "byte" -> 8;
      case "int16", "uint16" -> 16;
      case "int24", "uint24" -> 24;
      case "int32", "uint32" -> 32;
      case "int64", "uint64" -> 64;
      default -> null;
    };
  }

  private static int computeBytesToRead(int bitLength) {
    return (bitLength + 7) >>> 3;
  }

  private static long computeMask(int bitLength) {
    if (bitLength == 64) {
      return -1L;
    }
    if (bitLength > 0 && bitLength < 64) {
      return (1L << bitLength) - 1L;
    }
    return 0L;
  }

  private static long computeRawMin(boolean signed, int bitLength) {
    if (!signed) {
      return 0L;
    }

    if (bitLength == 64) {
      return Long.MIN_VALUE;
    }

    return -(1L << (bitLength - 1));
  }

  private static long computeRawMax(boolean signed, int bitLength) {
    if (signed) {
      if (bitLength == 64) {
        return Long.MAX_VALUE;
      }
      return (1L << (bitLength - 1)) - 1L;
    }

    if (bitLength == 64) {
      return -1L;
    }

    return (1L << bitLength) - 1L;
  }
}