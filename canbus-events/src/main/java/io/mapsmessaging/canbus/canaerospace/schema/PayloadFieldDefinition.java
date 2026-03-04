package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

@Data
public class PayloadFieldDefinition {
  private String name;
  private Integer byteOffset;
  private Integer byteLength;
  private String type;
}