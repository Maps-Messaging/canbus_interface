package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

@Data
public class NodeServiceChannelDefinition {
  private Integer channel;
  private Integer requestId;
  private Integer responseId;
}