package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

@Data
public class NodeServiceCodeDefinition {
  private String name;
  private Integer serviceCode;
  private NumericRange serviceCodeRange;
  private Boolean responseRequired;
  private String action;
}