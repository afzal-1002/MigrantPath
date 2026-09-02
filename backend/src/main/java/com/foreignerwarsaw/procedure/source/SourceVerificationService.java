package com.foreignerwarsaw.procedure.source;

import com.foreignerwarsaw.user.User;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a human verification outcome and applies it to the {@link OfficialSource} it checked
 * (brief §24). Deliberately no automated crawling/scheduling here - Phase 4 establishes
 * traceability, not monitoring (brief §24's own boundary).
 */
@Service
public class SourceVerificationService {

  private final OfficialSourceRepository officialSourceRepository;
  private final SourceVerificationRepository sourceVerificationRepository;
  private final Clock clock;

  public SourceVerificationService(
      OfficialSourceRepository officialSourceRepository,
      SourceVerificationRepository sourceVerificationRepository,
      Clock clock) {
    this.officialSourceRepository = officialSourceRepository;
    this.sourceVerificationRepository = sourceVerificationRepository;
    this.clock = clock;
  }

  @Transactional
  public SourceVerification recordVerification(
      UUID sourceId, User checkedBy, VerificationStatus status, String notes) {
    OfficialSource source =
        officialSourceRepository
            .findById(sourceId)
            .orElseThrow(() -> new OfficialSourceNotFoundException(sourceId));
    SourceVerification verification =
        new SourceVerification(source, clock.instant(), checkedBy, status, notes);
    sourceVerificationRepository.save(verification);
    source.applyVerification(verification);
    return verification;
  }
}
