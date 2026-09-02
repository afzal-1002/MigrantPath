package com.foreignerwarsaw.procedure.source;

/**
 * {@link OfficialSource#getVerificationStatus()} and {@link SourceVerification#getStatus()} share
 * this vocabulary - a completely different axis from {@code
 * com.foreignerwarsaw.procedure.PublicationStatus} (brief §22/§97): a source can be {@code
 * VERIFIED} while the legal-content interpretation built from it is still {@code IN_REVIEW}, or
 * vice versa in principle. Never confuse the two.
 */
public enum VerificationStatus {
  DRAFT,
  VERIFIED,
  NEEDS_REVIEW,
  OUTDATED,
  ARCHIVED
}
