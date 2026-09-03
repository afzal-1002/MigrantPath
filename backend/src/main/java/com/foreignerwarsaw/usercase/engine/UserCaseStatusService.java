package com.foreignerwarsaw.usercase.engine;

import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.usercase.core.UserCase;
import com.foreignerwarsaw.usercase.core.UserCaseEvent;
import com.foreignerwarsaw.usercase.core.UserCaseEventRepository;
import com.foreignerwarsaw.usercase.core.UserCaseEventType;
import com.foreignerwarsaw.usercase.core.UserCaseStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whole-case status transitions (brief §22/§59) - validated by {@code UserCaseStatusTransitions},
 * logged as a {@code CASE_STATUS_CHANGED} event every time. Called from within its own transaction
 * (never a pre-fetched, possibly-detached {@link UserCase}) - see {@link UserCaseAccessService}'s
 * Javadoc for why this ordering matters.
 */
@Service
public class UserCaseStatusService {

  private final UserCaseAccessService accessService;
  private final UserCaseEventRepository eventRepository;
  private final Clock clock;

  public UserCaseStatusService(
      UserCaseAccessService accessService, UserCaseEventRepository eventRepository, Clock clock) {
    this.accessService = accessService;
    this.eventRepository = eventRepository;
    this.clock = clock;
  }

  @Transactional
  public UserCase changeStatus(UUID caseId, UUID userId, UserCaseStatus newStatus, User actor) {
    UserCase userCase = accessService.getOwned(caseId, userId);
    UserCaseStatus previous = userCase.getStatus();
    Instant now = clock.instant();
    userCase.changeStatus(newStatus, now);

    eventRepository.save(
        new UserCaseEvent(
            userCase,
            UserCaseEventType.CASE_STATUS_CHANGED,
            now,
            actor,
            previous + " -> " + newStatus));
    if (newStatus == UserCaseStatus.CANCELLED) {
      eventRepository.save(
          new UserCaseEvent(userCase, UserCaseEventType.CASE_CANCELLED, now, actor, null));
    }
    return userCase;
  }
}
