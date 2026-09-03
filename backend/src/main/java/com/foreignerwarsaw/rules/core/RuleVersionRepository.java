package com.foreignerwarsaw.rules.core;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuleVersionRepository extends JpaRepository<RuleVersion, UUID> {

  @Query("SELECT v FROM RuleVersion v JOIN FETCH v.rule WHERE v.id = :id")
  Optional<RuleVersion> findByIdFetchingRule(@Param("id") UUID id);

  /**
   * The Active-Version Predicate for rules (docs/database/DATABASE.md §0) - the one authoritative
   * query production evaluation uses, mirroring {@code
   * ThresholdVersionRepository#findActivePublishedVersion} exactly.
   */
  @Query(
      """
      SELECT v FROM RuleVersion v
      WHERE v.rule.id = :ruleId
        AND v.status = com.foreignerwarsaw.procedure.PublicationStatus.PUBLISHED
        AND v.effectiveFrom <= :evaluationDate
        AND (v.effectiveTo IS NULL OR v.effectiveTo > :evaluationDate)
      """)
  Optional<RuleVersion> findActivePublishedVersion(
      @Param("ruleId") UUID ruleId, @Param("evaluationDate") LocalDate evaluationDate);

  @Query(
      "SELECT v FROM RuleVersion v WHERE v.rule.id = :ruleId AND v.status = com.foreignerwarsaw.procedure.PublicationStatus.PUBLISHED")
  List<RuleVersion> findPublishedVersions(@Param("ruleId") UUID ruleId);

  @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM RuleVersion v WHERE v.rule.id = :ruleId")
  int findMaxVersionNumber(@Param("ruleId") UUID ruleId);
}
