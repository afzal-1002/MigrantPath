package com.foreignerwarsaw.observability;

import com.foreignerwarsaw.procedure.core.ProcedureVersionSourceRepository;
import com.foreignerwarsaw.procedure.source.OfficialSourceRepository;
import com.foreignerwarsaw.procedure.source.VerificationStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Canonical Phase 14 (Observability) brief §33/§34/§35/§150 - legal-content *health*, deliberately
 * separate from technical readiness: an outdated {@code OfficialSource} is an operational warning
 * an editor/reviewer should act on, never a reason {@code /actuator/health/readiness} reports
 * {@code DOWN} (brief §35's own "do NOT make readiness DOWN merely because one source is OUTDATED"
 * - this class is never consulted by any health indicator, only by Micrometer/ Prometheus).
 *
 * <p>Both gauges are refreshed on a fixed schedule, not per-scrape (brief §150's own "do not
 * execute expensive multi-join legal audit every 15 seconds") - a {@link Gauge} reads whatever the
 * last scheduled refresh computed, an {@link AtomicLong} read being effectively free.
 */
@Component
public class LegalContentHealthMetrics {

  private final OfficialSourceRepository officialSourceRepository;
  private final ProcedureVersionSourceRepository procedureVersionSourceRepository;
  private final AtomicLong outdatedSources = new AtomicLong();
  private final AtomicLong publishedVersionsWithOutdatedSource = new AtomicLong();

  public LegalContentHealthMetrics(
      OfficialSourceRepository officialSourceRepository,
      ProcedureVersionSourceRepository procedureVersionSourceRepository,
      MeterRegistry meterRegistry) {
    this.officialSourceRepository = officialSourceRepository;
    this.procedureVersionSourceRepository = procedureVersionSourceRepository;
    Gauge.builder("legal.sources.outdated", outdatedSources, AtomicLong::get)
        .description(
            "OfficialSource rows currently marked OUTDATED (refreshed periodically, not live)")
        .register(meterRegistry);
    Gauge.builder(
            "legal.content.with_outdated_source",
            publishedVersionsWithOutdatedSource,
            AtomicLong::get)
        .description(
            "Currently PUBLISHED ProcedureVersions citing at least one OUTDATED OfficialSource (refreshed periodically, not live)")
        .register(meterRegistry);
  }

  /**
   * Every 30 minutes is frequent enough for an operational warning about content review staleness
   * (brief §52's own "routine review item, not a 3 AM page") without adding real load to the two
   * underlying queries on any meaningful cadence. No {@code @Transactional} needed here (and
   * deliberately none added, after Phase 13's own self-invocation lesson): both calls below are
   * plain {@code JpaRepository} query methods, each already transactional on its own per Spring
   * Data's default behavior - no shared transactional boundary across the two calls is required, so
   * there is no self-invocation proxy pitfall to avoid in the first place.
   */
  @Scheduled(fixedDelayString = "PT30M")
  public void refresh() {
    outdatedSources.set(
        officialSourceRepository.countByVerificationStatus(VerificationStatus.OUTDATED));
    publishedVersionsWithOutdatedSource.set(
        procedureVersionSourceRepository.countPublishedVersionsWithOutdatedSource());
  }
}
