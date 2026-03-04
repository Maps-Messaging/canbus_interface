package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TimeSlotAllocationExample {
  private String txSlot;
  private String parameterName;
  private String units;
  private BigDecimal transmissionIntervalMs;
  private Integer canId;
  private String dataType;
}