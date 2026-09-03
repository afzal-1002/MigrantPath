package com.foreignerwarsaw.usercase.engine.dto;

import com.foreignerwarsaw.usercase.core.UserCaseEvent;
import java.time.Instant;

/** brief §82 - a meaningful, user-facing timeline entry, never raw DB noise. */
public record CaseEventResponse(String eventType, Instant occurredAt, String metadata) {

  public static CaseEventResponse from(UserCaseEvent event) {
    return new CaseEventResponse(
        event.getEventType().name(), event.getOccurredAt(), event.getMetadata());
  }
}
