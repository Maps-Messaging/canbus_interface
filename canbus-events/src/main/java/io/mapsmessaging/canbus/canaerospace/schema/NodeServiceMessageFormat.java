package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NodeServiceMessageFormat {
  private Integer nodeId;
  private String dataType;
  private Integer serviceCode;
  private Integer messageCode;
  private String data;
  private List<String> dataBytes = new ArrayList<>();
}