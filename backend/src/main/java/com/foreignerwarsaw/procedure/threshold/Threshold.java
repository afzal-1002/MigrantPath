package com.foreignerwarsaw.procedure.threshold;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A standalone, independently-versioned numeric fact (e.g. a future Blue Card salary minimum) a
 * Phase 6 {@code RuleCondition} will reference by code - unlike {@code Fee}, not owned by any one
 * {@code Procedure} (docs/database/DATABASE.md §3, IMPLEMENTATION_PLAN.md 4.6). No rows are seeded
 * in Phase 4 (brief §21/§53: never seed unverified legal numeric thresholds).
 */
@Entity
@Table(name = "thresholds")
public class Threshold {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(name = "canonical_name", nullable = false, length = 200)
  private String canonicalName;

  @Enumerated(EnumType.STRING)
  @Column(name = "value_type", nullable = false, length = 20)
  private ThresholdValueType valueType;

  @Column(length = 50)
  private String unit;

  @Column(length = 3)
  private String currency;

  @Column(nullable = false)
  private boolean active = true;

  protected Threshold() {}

  public Threshold(String code, String canonicalName, ThresholdValueType valueType) {
    this.code = code;
    this.canonicalName = canonicalName;
    this.valueType = valueType;
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getCanonicalName() {
    return canonicalName;
  }

  public ThresholdValueType getValueType() {
    return valueType;
  }

  public String getUnit() {
    return unit;
  }

  public boolean isActive() {
    return active;
  }
}
