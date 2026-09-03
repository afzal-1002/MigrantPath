package com.foreignerwarsaw.usercase.core;

/**
 * brief §11/§14 - checklist *progress* (has the user got this document ready), deliberately
 * distinct from {@link UserCaseDocumentApplicability} (whether the document is even relevant).
 * {@code NEEDS_UPDATE} is set only by an upgrade when a previously-{@code READY} document's
 * requirement materially changed (brief §36, docs/cases/REQUIREMENT_CHANGE_POLICY.md).
 */
public enum UserCaseDocumentStatus {
  NOT_STARTED,
  MISSING,
  IN_PROGRESS,
  READY,
  NEEDS_UPDATE,
  NOT_APPLICABLE
}
