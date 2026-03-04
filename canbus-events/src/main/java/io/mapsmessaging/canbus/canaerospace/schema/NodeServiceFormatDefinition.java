package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

@Data
public class NodeServiceFormatDefinition {
  private NodeServiceMessageFormat request;
  private NodeServiceMessageFormat response;
  private NodeServiceMessageFormat checksumResponse;
}