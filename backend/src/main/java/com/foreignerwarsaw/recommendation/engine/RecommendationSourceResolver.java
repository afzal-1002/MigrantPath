package com.foreignerwarsaw.recommendation.engine;

import com.foreignerwarsaw.procedure.core.ProcedureVersionSourceRepository;
import com.foreignerwarsaw.procedure.source.OfficialSource;
import com.foreignerwarsaw.procedure.source.SourceRole;
import com.foreignerwarsaw.rules.core.RuleVersionSourceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a recommendation's combined, deduplicated official-source list (brief §30/§56): the
 * active {@code ProcedureVersion}'s own sources, plus every {@code RuleVersion}'s sources for the
 * rules that produced it. Computed fresh at read time from the immutable version ids a {@code
 * Recommendation}/{@code RecommendationReason} already stores - never persisted as its own join
 * table (see PHASE_7_REPORT.md "Deviations"), since a published version's own source associations
 * never change after the fact, so recomputing from the stored version ids is exactly as
 * reproducible as persisting a copy would be.
 *
 * <p>Threshold-version sources are deliberately not included (see PHASE_7_REPORT.md "Deviations") -
 * doing so would need a further child table recording which {@code ThresholdVersion}s a
 * recommendation's rules actually used, which no production {@code Threshold} content exists yet to
 * make worthwhile (Phase 6's own seed-data policy).
 */
@Service
public class RecommendationSourceResolver {

  /** Lower rank = shown first (brief §56: "PRIMARY/legal basis first, supporting afterward"). */
  private static final Map<SourceRole, Integer> ROLE_RANK =
      Map.of(
          SourceRole.LEGAL_BASIS, 0,
          SourceRole.PRIMARY, 1,
          SourceRole.SUPPORTING, 2,
          SourceRole.OPERATIONAL, 3);

  private final ProcedureVersionSourceRepository procedureVersionSourceRepository;
  private final RuleVersionSourceRepository ruleVersionSourceRepository;

  public RecommendationSourceResolver(
      ProcedureVersionSourceRepository procedureVersionSourceRepository,
      RuleVersionSourceRepository ruleVersionSourceRepository) {
    this.procedureVersionSourceRepository = procedureVersionSourceRepository;
    this.ruleVersionSourceRepository = ruleVersionSourceRepository;
  }

  @Transactional(readOnly = true)
  public List<ResolvedSource> resolve(UUID procedureVersionId, Set<UUID> ruleVersionIds) {
    Map<UUID, ResolvedSource> bySourceId = new LinkedHashMap<>();

    if (procedureVersionId != null) {
      procedureVersionSourceRepository
          .findByProcedureVersion_Id(procedureVersionId)
          .forEach(s -> merge(bySourceId, s.getOfficialSource(), s.getRole()));
    }
    for (UUID ruleVersionId : ruleVersionIds) {
      if (ruleVersionId == null) {
        continue;
      }
      ruleVersionSourceRepository
          .findByRuleVersion_Id(ruleVersionId)
          .forEach(s -> merge(bySourceId, s.getOfficialSource(), s.getRole()));
    }

    return bySourceId.values().stream()
        .sorted(
            java.util.Comparator.<ResolvedSource>comparingInt(
                    rs -> ROLE_RANK.getOrDefault(rs.role(), 9))
                .thenComparing(rs -> rs.source().getTitle()))
        .toList();
  }

  private void merge(Map<UUID, ResolvedSource> bySourceId, OfficialSource source, SourceRole role) {
    ResolvedSource existing = bySourceId.get(source.getId());
    if (existing == null
        || ROLE_RANK.getOrDefault(role, 9) < ROLE_RANK.getOrDefault(existing.role(), 9)) {
      bySourceId.put(source.getId(), new ResolvedSource(source, role));
    }
  }
}
