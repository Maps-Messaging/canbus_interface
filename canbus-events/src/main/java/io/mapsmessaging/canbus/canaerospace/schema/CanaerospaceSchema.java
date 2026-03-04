package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CanaerospaceSchema {

  private SchemaMeta meta;
  private List<MessageTypeDefinition> messageTypes = new ArrayList<>();
  private FrameDefinition frame;
  private DataTypesDefinition dataTypes;
  private NodeServicesDefinition nodeServices;
  private List<IdentifierDefinition> identifiers = new ArrayList<>();
  private ValidationDefinition validation;
  private List<IdentifierRangeDefinition> identifierRanges = new ArrayList<>();
  private List<NativeFormatChannel> nativeFormatChannels = new ArrayList<>();
  private List<NodeIdExample> baselineNodeIdExamples = new ArrayList<>();
  private List<TimeSlotAllocationExample> timeSlotAllocationExamples = new ArrayList<>();
}