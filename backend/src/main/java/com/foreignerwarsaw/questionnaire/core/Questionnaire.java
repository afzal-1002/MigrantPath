package com.foreignerwarsaw.questionnaire.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Stable questionnaire identity (docs/database/DATABASE.md §4, brief §3) - answers only "which
 * questionnaire is this" (e.g. {@code WARSAW_GENERAL_ASSESSMENT}), never any content itself;
 * content lives entirely on {@link QuestionnaireVersion}. Mirrors {@link
 * com.foreignerwarsaw.procedure.core.Procedure}'s identity+version split.
 */
@Entity
@Table(name = "questionnaires")
public class Questionnaire {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(name = "canonical_name", nullable = false, length = 300)
  private String canonicalName;

  @Column(nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Questionnaire() {}

  public static Questionnaire create(String code, String canonicalName) {
    Questionnaire questionnaire = new Questionnaire();
    questionnaire.code = code;
    questionnaire.canonicalName = canonicalName;
    return questionnaire;
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

  public boolean isActive() {
    return active;
  }
}
