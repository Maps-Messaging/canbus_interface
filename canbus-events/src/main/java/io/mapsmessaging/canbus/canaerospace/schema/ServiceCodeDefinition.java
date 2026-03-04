package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

@Data
public class ServiceCodeDefinition {

  private String name;

  private Integer serviceCode;

  private NumericRangeInteger serviceCodeRange;

  private Boolean responseRequired;

  private String action;
}