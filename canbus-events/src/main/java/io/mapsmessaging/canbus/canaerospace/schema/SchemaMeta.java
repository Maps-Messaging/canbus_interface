package io.mapsmessaging.canbus.canaerospace.schema;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SchemaMeta {
  private String schema;
  private String version;
  private String generated;
  private List<String> notes = new ArrayList<>();
}