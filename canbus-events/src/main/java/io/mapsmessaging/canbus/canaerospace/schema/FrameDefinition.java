package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

@Data
public class FrameDefinition {
  private FrameDlcDefinition dlc;
  private String endianness;
  private java.util.List<PayloadFieldDefinition> payloadLayout;
}