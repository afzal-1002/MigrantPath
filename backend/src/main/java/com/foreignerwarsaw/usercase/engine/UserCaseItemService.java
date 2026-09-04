package com.foreignerwarsaw.usercase.engine;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.usercase.core.UserCase;
import com.foreignerwarsaw.usercase.core.UserCaseDocument;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentRepository;
import com.foreignerwarsaw.usercase.core.UserCaseDocumentStatus;
import com.foreignerwarsaw.usercase.core.UserCaseEvent;
import com.foreignerwarsaw.usercase.core.UserCaseEventRepository;
import com.foreignerwarsaw.usercase.core.UserCaseEventType;
import com.foreignerwarsaw.usercase.core.UserCaseFee;
import com.foreignerwarsaw.usercase.core.UserCaseFeeRepository;
import com.foreignerwarsaw.usercase.core.UserCaseFeeStatus;
import com.foreignerwarsaw.usercase.core.UserCaseStatus;
import com.foreignerwarsaw.usercase.core.UserCaseStep;
import com.foreignerwarsaw.usercase.core.UserCaseStepRepository;
import com.foreignerwarsaw.usercase.core.UserCaseStepStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step/document/fee checklist status updates (brief §60/§61) - ownership, case-not-cancelled/
 * completed, item-belongs-to-the-current-revision, and item-transition validation, every time.
 * Snapshot fields ({@code mandatory}, copies, translation requirement, ...) are never writable here
 * (brief §61 - "do not allow user to change... those are snapshot data"), only the item's own
 * status and (for documents) the user's free-text note.
 */
@Service
public class UserCaseItemService {

  private final UserCaseAccessService accessService;
  private final UserCaseStepRepository stepRepository;
  private final UserCaseDocumentRepository documentRepository;
  private final UserCaseFeeRepository feeRepository;
  private final UserCaseEventRepository eventRepository;
  private final Clock clock;

  public UserCaseItemService(
      UserCaseAccessService accessService,
      UserCaseStepRepository stepRepository,
      UserCaseDocumentRepository documentRepository,
      UserCaseFeeRepository feeRepository,
      UserCaseEventRepository eventRepository,
      Clock clock) {
    this.accessService = accessService;
    this.stepRepository = stepRepository;
    this.documentRepository = documentRepository;
    this.feeRepository = feeRepository;
    this.eventRepository = eventRepository;
    this.clock = clock;
  }

  @Transactional
  public UserCaseStep updateStepStatus(
      UUID caseId, UUID userId, UUID stepId, UserCaseStepStatus newStatus, User actor) {
    UserCase userCase = accessService.getOwned(caseId, userId);
    requireEditable(userCase);
    UserCaseStep step = requireCurrentRevisionItem(userCase, stepRepository.findById(stepId));

    UserCaseStepStatus previous = step.getStatus();
    Instant now = clock.instant();
    step.changeStatus(newStatus, now);
    userCase.touch(now);

    if (newStatus == UserCaseStepStatus.COMPLETED) {
      eventRepository.save(
          new UserCaseEvent(
              userCase, UserCaseEventType.STEP_COMPLETED, now, actor, step.getStableCode()));
    } else if (previous == UserCaseStepStatus.COMPLETED) {
      eventRepository.save(
          new UserCaseEvent(
              userCase, UserCaseEventType.STEP_REOPENED, now, actor, step.getStableCode()));
    }
    return step;
  }

  @Transactional
  public UserCaseDocument updateDocumentStatus(
      UUID caseId, UUID userId, UUID documentId, UserCaseDocumentStatus newStatus, User actor) {
    UserCase userCase = accessService.getOwned(caseId, userId);
    requireEditable(userCase);
    UserCaseDocument document =
        requireCurrentRevisionItem(userCase, documentRepository.findById(documentId));

    UserCaseDocumentStatus previous = document.getStatus();
    Instant now = clock.instant();
    document.changeStatus(newStatus, now);
    userCase.touch(now);

    eventRepository.save(
        new UserCaseEvent(
            userCase,
            UserCaseEventType.DOCUMENT_STATUS_CHANGED,
            now,
            actor,
            document.getStableCode() + ": " + previous + " -> " + newStatus));
    return document;
  }

  @Transactional
  public UserCaseDocument updateDocumentNote(
      UUID caseId, UUID userId, UUID documentId, String note, User actor) {
    UserCase userCase = accessService.getOwned(caseId, userId);
    requireEditable(userCase);
    UserCaseDocument document =
        requireCurrentRevisionItem(userCase, documentRepository.findById(documentId));
    document.setUserNote(note, clock.instant());
    return document;
  }

  @Transactional
  public UserCaseFee updateFeeStatus(
      UUID caseId, UUID userId, UUID feeId, UserCaseFeeStatus newStatus, User actor) {
    if (newStatus == UserCaseFeeStatus.NOT_APPLICABLE) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CASE_ITEM_TRANSITION_INVALID",
          "NOT_APPLICABLE is not a status a user can set directly");
    }
    UserCase userCase = accessService.getOwned(caseId, userId);
    requireEditable(userCase);
    UserCaseFee fee = requireCurrentRevisionItem(userCase, feeRepository.findById(feeId));

    UserCaseFeeStatus previous = fee.getStatus();
    Instant now = clock.instant();
    fee.changeStatus(newStatus, now);
    userCase.touch(now);

    eventRepository.save(
        new UserCaseEvent(
            userCase,
            UserCaseEventType.FEE_STATUS_CHANGED,
            now,
            actor,
            fee.getStableCode() + ": " + previous + " -> " + newStatus));
    return fee;
  }

  private void requireEditable(UserCase userCase) {
    if (userCase.getStatus() == UserCaseStatus.CANCELLED
        || userCase.getStatus() == UserCaseStatus.COMPLETED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CASE_STATUS_TRANSITION_INVALID",
          "This case is " + userCase.getStatus() + " and its checklist can no longer be edited");
    }
  }

  // A tiny shared helper so the three item lookups share one not-found + ownership + wrong-
  // revision check without three near-identical method bodies.
  //
  // Canonical Phase 12 (Security/Privacy/GDPR) brief §30/§72 - hardens what canonical Phase 11
  // (Testing) found and only *proved* safe, not *made* safe: this used to check only whether the
  // resolved item's revision matched the case's current revision, which happened to also reject
  // a cross-case item id (since revision ids are never shared across cases) but never actually
  // asked "does this item belong to the case the caller is authorized for" directly. That's now
  // the first, explicit check below - same 404 CASE_ITEM_NOT_FOUND convention every other owned
  // resource in this codebase uses for ownership-hiding (brief §9's IDOR discipline), so a
  // cross-case id and a genuinely-nonexistent id are indistinguishable to the caller either way.
  // The historical-revision check (409 CASE_ITEM_NOT_APPLICABLE) only ever runs once ownership of
  // the *case* itself is already established - a user may only mutate an item belonging to the
  // case's *current* revision; a historical revision's items are read-only (brief §105).
  private <T> T requireCurrentRevisionItem(UserCase userCase, java.util.Optional<T> found) {
    T item =
        found.orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND, "CASE_ITEM_NOT_FOUND", "No case item found"));
    if (!userCase.getId().equals(caseIdOf(item))) {
      // Deliberately the identical exception the "not found at all" branch above throws - never
      // reveal that an item with this id exists in some other case.
      throw new ApiException(HttpStatus.NOT_FOUND, "CASE_ITEM_NOT_FOUND", "No case item found");
    }
    UUID revisionId = revisionIdOf(item);
    if (userCase.getCurrentRevision() == null
        || !userCase.getCurrentRevision().getId().equals(revisionId)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CASE_ITEM_NOT_APPLICABLE",
          "This item belongs to a historical revision and can no longer be updated directly");
    }
    return item;
  }

  private UUID revisionIdOf(Object item) {
    if (item instanceof UserCaseStep step) {
      return step.getSnapshotRevision().getId();
    }
    if (item instanceof UserCaseDocument document) {
      return document.getSnapshotRevision().getId();
    }
    if (item instanceof UserCaseFee fee) {
      return fee.getSnapshotRevision().getId();
    }
    throw new IllegalStateException("Unknown case item type: " + item.getClass());
  }

  private UUID caseIdOf(Object item) {
    if (item instanceof UserCaseStep step) {
      return step.getSnapshotRevision().getUserCase().getId();
    }
    if (item instanceof UserCaseDocument document) {
      return document.getSnapshotRevision().getUserCase().getId();
    }
    if (item instanceof UserCaseFee fee) {
      return fee.getSnapshotRevision().getUserCase().getId();
    }
    throw new IllegalStateException("Unknown case item type: " + item.getClass());
  }
}
