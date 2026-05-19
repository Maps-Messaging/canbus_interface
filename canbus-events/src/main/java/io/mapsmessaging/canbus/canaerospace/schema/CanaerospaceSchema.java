/*
 *   Copyright [ 2024 -  2026 ] MapsMessaging B.V.
 *
 *   Licensed under the Apache License, Version 2.0 with the Commons Clause
 *   (the "License"); you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at:
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *       https://commonsclause.com/
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

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