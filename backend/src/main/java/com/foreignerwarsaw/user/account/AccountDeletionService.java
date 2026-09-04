package com.foreignerwarsaw.user.account;

import com.foreignerwarsaw.common.audit.AuditActionType;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.common.audit.AuditService;
import com.foreignerwarsaw.common.security.SecurityEventLogger;
import com.foreignerwarsaw.common.security.SessionInvalidator;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canonical Phase 12 (Security/Privacy/GDPR) - self-service account deletion (brief §17-§35).
 *
 * <p><b>Why this does not manually orchestrate deleting every personal-data table</b> (contrary to
 * a first reading of "do not rely solely on Hibernate cascading"): that instruction targets
 * <em>JPA-level</em> object-graph cascade (loading an entire entity graph into memory and letting
 * Hibernate walk {@code @OneToMany(cascade=...)} associations one row at a time) - {@link User}
 * declares no such cascade to any personal-data entity. What this service relies on instead is the
 * <em>database's own</em> {@code ON DELETE CASCADE} foreign-key constraints, audited row by row
 * against every migration referencing {@code users(id)} before this class was written (see
 * docs/product/PHASE_12_REPORT.md's FK audit table): {@code user_roles}, {@code
 * email_verification_tokens}, {@code password_reset_tokens}, {@code user_consents}, {@code
 * assessments} (and transitively {@code assessment_answers}), {@code recommendation_runs} (and
 * transitively {@code recommendations}), and {@code user_cases} (and transitively every snapshot
 * revision/step/document/fee/event) are all real, deliberate {@code ON DELETE CASCADE} constraints
 * - a single {@code DELETE FROM users WHERE id = ?} is both correct and atomic, verified against
 * the schema rather than assumed. Governance/audit references ({@code ProcedureVersion}/{@code
 * RuleVersion}/{@code ThresholdVersion}/{@code QuestionnaireVersion} actors, {@code
 * OfficialSource.checkedBy}, {@code AuditLog.actor}, {@code UserCaseEvent.actor}, and - as of V48 -
 * {@code AdminReview.submittedBy}) are all {@code ON DELETE SET NULL}, so deleting a user never
 * breaks legal-content or governance history (brief §7/§24/§26).
 */
@Service
public class AccountDeletionService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionInvalidator sessionInvalidator;
  private final SecurityEventLogger securityEventLogger;
  private final AuditService auditService;
  private final Clock clock;

  public AccountDeletionService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      SessionInvalidator sessionInvalidator,
      SecurityEventLogger securityEventLogger,
      AuditService auditService,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.sessionInvalidator = sessionInvalidator;
    this.securityEventLogger = securityEventLogger;
    this.auditService = auditService;
    this.clock = clock;
  }

  /**
   * Deletes the authenticated account and its entire personal-data graph. Transactional: a failure
   * at any point (including the audit writes) rolls back the whole operation - the account is
   * either fully present or fully gone, never half-deleted (brief §10/§34).
   *
   * <p>Both audit rows are written <em>before</em> the {@code DELETE FROM users}, in the same
   * transaction, while {@code user} is still a valid foreign-key target - the database then sets
   * their {@code actor_user_id} to {@code NULL} as part of the same cascade the moment the user row
   * is actually removed at commit. The account's own id is recorded in {@code entityId} (an
   * internal UUID, never email/name) so the pair of rows remains privacy-safely traceable after the
   * account is gone (brief §27/§177).
   */
  @Transactional
  public void deleteOwnAccount(UUID userId, String currentPassword) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () ->
                    new IllegalStateException("Authenticated principal has no matching user row"));

    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "ACCOUNT_REAUTHENTICATION_FAILED",
          "Current password is incorrect");
    }

    UUID accountId = user.getId();
    String email = user.getEmail();

    // A real bug found this phase: passing `user` (or even a separate entityManager.getReference()
    // proxy for the same row - also tried) as the AuditLog actor makes Hibernate's flush-order
    // dependency check throw TransientPropertyValueException, because that same User row is *also*
    // pending removal later in this very transaction - regardless of call order or an explicit
    // intermediate flush(), Hibernate's cascade-checker refuses to resolve a new association
    // against
    // an entity that is simultaneously scheduled for deletion in the same persistence context.
    // AuditLog.actor is nullable by design specifically for this situation (its own Javadoc/V46
    // migration: ON DELETE SET NULL) - passing null directly here reaches the exact same end state
    // (no actor, since the account won't exist moments later) far more simply than fighting
    // Hibernate's entity-instance bookkeeping. The account's own id is still recorded in entityId.
    auditService.record(
        null,
        AuditActionType.ACCOUNT_DELETION_REQUESTED,
        AuditEntityType.USER,
        accountId,
        null,
        null,
        "Account deletion requested");

    // Kill every active session for this account, including the one making this very request -
    // same documented choice UserAccountService#changePassword already makes (brief §31: "current
    // request must not continue as authenticated user afterward").
    sessionInvalidator.invalidateAllSessionsFor(email);

    auditService.record(
        null,
        AuditActionType.ACCOUNT_DELETION_COMPLETED,
        AuditEntityType.USER,
        accountId,
        null,
        null,
        "Account deletion completed");

    securityEventLogger.log(SecurityEventLogger.Event.ACCOUNT_DELETED, accountId.toString());

    // Verification/reset tokens, consents, assessments, recommendation runs, and cases all cascade
    // from this single delete - see this class's own Javadoc for the audited FK table.
    userRepository.delete(user);
  }
}
