package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

@Data
public class IdentifierRangeDefinition {
  private IdRange idRange;
  private HexRange hexRange;
  private String group;
  private String titleTemplate;
  private String messageType;
  private String dataType;
  private String units;
  private NumericRange range;
  private Double resolution;
  private String notes;
}