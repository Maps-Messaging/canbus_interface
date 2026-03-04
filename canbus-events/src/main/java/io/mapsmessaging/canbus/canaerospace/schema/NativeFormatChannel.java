package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

@Data
public class NativeFormatChannel {
  private String channel;
  private Integer ddsRequestCanId;
  private Integer ddsResponseCanId;
}