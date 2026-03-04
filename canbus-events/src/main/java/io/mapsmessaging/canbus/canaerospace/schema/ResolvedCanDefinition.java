package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class ResolvedCanDefinition {
  int canId;
  String hex;
  String group;
  String messageType;
  String title;
  String name;
  String dataType;
  String units;
  NumericRange range;
  Double resolution;
  String notes;
  boolean isRangeDerived;
  Map<String, Object> templateVariables;
}