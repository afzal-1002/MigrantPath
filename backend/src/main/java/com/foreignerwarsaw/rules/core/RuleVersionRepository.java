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

  /**
   * Phase 9 admin listing (brief §36) - newest version first. Fetch-joins {@code rule} and every
   * actor field {@code AdminRuleVersionDetailResponse} reads, so the (non-{@code @Transactional})
   * admin controller can map the response after this call returns without a
   * LazyInitializationException - see {@code ProcedureVersionRepository
   * #findByProcedure_IdAndVersionNumberFetchingActors}'s Javadoc for the same fix applied there.
   */
  @Query(
      """
      SELECT v FROM RuleVersion v
      JOIN FETCH v.rule
      LEFT JOIN FETCH v.createdBy
      LEFT JOIN FETCH v.submittedBy
      LEFT JOIN FETCH v.approvedBy
      LEFT JOIN FETCH v.publishedBy
      WHERE v.rule.id = :ruleId ORDER BY v.versionNumber DESC
      """)
  List<RuleVersion> findByRule_IdOrderByVersionNumberDesc(@Param("ruleId") UUID ruleId);

  default List<RuleVersion> findByRule_Id(UUID ruleId) {
    return findByRule_IdOrderByVersionNumberDesc(ruleId);
  }

  /** Phase 9 admin dashboard addition (brief §16). */
  long countByStatus(com.foreignerwarsaw.procedure.PublicationStatus status);
}
