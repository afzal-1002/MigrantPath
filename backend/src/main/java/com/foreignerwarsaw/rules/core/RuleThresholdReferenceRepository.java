package com.foreignerwarsaw.rules.core;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuleThresholdReferenceRepository
    extends JpaRepository<RuleThresholdReference, RuleThresholdReferenceId> {

  List<RuleThresholdReference> findByRuleVersion_Id(UUID ruleVersionId);

  /**
   * "Which rules depend on threshold X" (brief §21/§117) - the whole point of this table. Explicit
   * JPQL rather than a derived-embedded-id method name, to avoid any ambiguity in how Spring Data
   * resolves a nested {@code @EmbeddedId} property path.
   */
  @Query("SELECT r FROM RuleThresholdReference r WHERE r.id.thresholdCode = :thresholdCode")
  List<RuleThresholdReference> findByThresholdCode(@Param("thresholdCode") String thresholdCode);
}
