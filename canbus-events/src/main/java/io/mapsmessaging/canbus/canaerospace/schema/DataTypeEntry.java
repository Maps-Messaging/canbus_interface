package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;


@Data
public class DataTypeEntry {
  private Integer typeNumber;
  private String hex;
  private String bits;
  private NumericRange range;
  private String explanation;
}