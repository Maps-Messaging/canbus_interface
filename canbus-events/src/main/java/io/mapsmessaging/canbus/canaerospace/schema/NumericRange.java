package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class NumericRange {
  private BigDecimal min;
  private BigDecimal max;
}