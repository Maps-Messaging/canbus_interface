package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

@Data
public class MessageTypeDefinition {
  private String name;
  private String title;
  private IdRange idRange;
  private HexRange hexRange;
  private Integer channels;
  private String explanation;
}