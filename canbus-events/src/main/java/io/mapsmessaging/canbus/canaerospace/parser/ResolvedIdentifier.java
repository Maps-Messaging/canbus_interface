package io.mapsmessaging.canbus.canaerospace.parser;

import io.mapsmessaging.canbus.canaerospace.schema.IdentifierDefinition;
import io.mapsmessaging.canbus.canaerospace.schema.IdentifierRangeDefinition;
import lombok.Getter;

@Getter
public class ResolvedIdentifier {

  private final Integer canId;
  private final String group;
  private final String title;
  private final String name;
  private final String dataType;
  private final String units;
  private final Double resolution;
  private final String notes;
  private final Double rangeMin;
  private final Double rangeMax;

  public ResolvedIdentifier(Integer canId,
                             String group,
                             String title,
                             String name,
                             String dataType,
                             String units,
                             Double resolution,
                             String notes,
                             Double rangeMin,
                             Double rangeMax) {
    this.canId = canId;
    this.group = group;
    this.title = title;
    this.name = name;
    this.dataType = dataType;
    this.units = units;
    this.resolution = resolution;
    this.notes = notes;
    this.rangeMin = rangeMin;
    this.rangeMax = rangeMax;
  }

  public static ResolvedIdentifier fromExact(IdentifierDefinition definition) {
    Double min = null;
    Double max = null;
    if (definition.getRange() != null) {
      if (definition.getRange().getMin() != null) {
        min = definition.getRange().getMin().doubleValue();
      }
      if (definition.getRange().getMax() != null) {
        max = definition.getRange().getMax().doubleValue();
      }
    }

    Double resolution = definition.getResolution();

    return new ResolvedIdentifier(
        definition.getId(),
        definition.getGroup(),
        definition.getTitle(),
        definition.getName(),
        definition.getDataType(),
        definition.getUnits(),
        resolution,
        definition.getNotes(),
        min,
        max
    );
  }

  public static ResolvedIdentifier fromRange(int canId, IdentifierRangeDefinition range) {
    int base = range.getIdRange().getMin();
    int n = (canId - base) + 1;

    String title = range.getTitleTemplate();
    if (title != null) {
      title = title.replace("#{n}", Integer.toString(n));
    }

    Double min = null;
    Double max = null;
    if (range.getRange() != null) {
      if (range.getRange().getMin() != null) {
        min = range.getRange().getMin().doubleValue();
      }
      if (range.getRange().getMax() != null) {
        max = range.getRange().getMax().doubleValue();
      }
    }

    return new ResolvedIdentifier(
        canId,
        range.getGroup(),
        title,
        null,
        range.getDataType(),
        range.getUnits(),
        range.getResolution(),
        range.getNotes(),
        min,
        max
    );
  }
}