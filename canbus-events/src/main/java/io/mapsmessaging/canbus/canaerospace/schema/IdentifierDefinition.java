package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

@Data
public class IdentifierDefinition {
  private Integer id;
  private String hex;
  private String group;
  private String title;
  private String name;
  private String messageType;
  private String dataType;
  private String units;
  private NumericRange range;
  private Double resolution;
  private String notes;
}