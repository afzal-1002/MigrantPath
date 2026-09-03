package com.foreignerwarsaw.rules.core;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuleVersionSourceRepository
    extends JpaRepository<RuleVersionSource, RuleVersionSourceId> {

  /**
   * Phase 9 addition - fetch-joins {@code officialSource}, read by {@code
   * AdminRuleVersionDetailResponse.Source#from} after this repository call's own transaction has
   * closed (the same LazyInitializationException class of bug fixed throughout Phase 9's other
   * admin repositories). {@code RulePublishingService}'s own pre-existing use of this method never
   * touches {@code officialSource} beyond its {@code verificationStatus}/{@code role} inside its
   * own transaction, so widening the fetch here is safe for that caller too.
   */
  @Query(
      "SELECT s FROM RuleVersionSource s JOIN FETCH s.officialSource WHERE s.ruleVersion.id = :ruleVersionId")
  List<RuleVersionSource> findByRuleVersion_Id(@Param("ruleVersionId") UUID ruleVersionId);

  /** Phase 9 source-impact addition (brief §33/§34). */
  long countByOfficialSource_Id(UUID officialSourceId);
}
