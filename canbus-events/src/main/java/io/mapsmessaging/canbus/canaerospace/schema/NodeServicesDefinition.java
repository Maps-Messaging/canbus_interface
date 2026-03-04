package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class NodeServicesDefinition {
  private Map<String, List<NodeServiceChannelDefinition>> channels = new LinkedHashMap<>();
  private List<NodeServiceCodeDefinition> serviceCodes;
  private Map<String, NodeServiceFormatDefinition> formats = new LinkedHashMap<>();
}