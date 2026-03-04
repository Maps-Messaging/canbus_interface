package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DataTypesDefinition {
  private Map<String, DataTypeEntry> byName = new LinkedHashMap<>();
  private Map<Integer, String> byNumber = new LinkedHashMap<>();
}