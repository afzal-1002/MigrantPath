package com.foreignerwarsaw.procedure.threshold;

/**
 * DECIMAL/INTEGER/PERCENTAGE/DURATION/MONEY populate {@link ThresholdVersion#getValue()}; TEXT
 * populates {@link ThresholdVersion#getValueText()} instead - two nullable columns, not a fully
 * generic EAV design (brief §21: "avoid overengineering polymorphic values").
 */
public enum ThresholdValueType {
  DECIMAL,
  INTEGER,
  PERCENTAGE,
  DURATION,
  MONEY,
  TEXT
}
