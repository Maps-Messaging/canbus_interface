package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

@Data
public class ValidationDefinition {
  private Integer dlcMustBe;
  private String unknownIds;
  private String unknownDataType;
}