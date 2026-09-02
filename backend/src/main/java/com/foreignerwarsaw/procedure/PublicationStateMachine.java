package com.foreignerwarsaw.procedure;

import com.foreignerwarsaw.common.web.ApiException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * The one authoritative set of legal transitions (brief §8), shared by every entity using {@link
 * PublicationStatus} - never duplicated per entity. Forward: {@code DRAFT -> IN_REVIEW -> APPROVED
 * -> PUBLISHED -> ARCHIVED}. Justified reverse: {@code IN_REVIEW -> DRAFT}, {@code APPROVED ->
 * DRAFT} (send back for rework). {@code PUBLISHED -> ARCHIVED} is also allowed directly (brief
 * §110/§111 - withdrawing already-published content), bypassing IN_REVIEW/APPROVED. No privileged
 * "skip a step" override exists (brief §8: "Do NOT allow arbitrary DRAFT -> PUBLISHED... preferably
 * not in MVP").
 */
public final class PublicationStateMachine {

  private static final Map<PublicationStatus, Set<PublicationStatus>> ALLOWED_TRANSITIONS =
      Map.of(
          PublicationStatus.DRAFT, Set.of(PublicationStatus.IN_REVIEW),
          PublicationStatus.IN_REVIEW, Set.of(PublicationStatus.APPROVED, PublicationStatus.DRAFT),
          PublicationStatus.APPROVED, Set.of(PublicationStatus.PUBLISHED, PublicationStatus.DRAFT),
          PublicationStatus.PUBLISHED, Set.of(PublicationStatus.ARCHIVED),
          PublicationStatus.ARCHIVED, Set.of());

  private PublicationStateMachine() {}

  public static boolean isAllowed(PublicationStatus from, PublicationStatus to) {
    return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
  }

  /**
   * Throws a 409-style {@link ApiException} rather than returning a boolean when the caller has no
   * useful fallback besides rejecting the request outright (every controller/service call site in
   * this codebase).
   */
  public static void requireAllowed(PublicationStatus from, PublicationStatus to) {
    if (!isAllowed(from, to)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "INVALID_STATUS_TRANSITION",
          "Cannot transition from %s to %s".formatted(from, to));
    }
  }
}
