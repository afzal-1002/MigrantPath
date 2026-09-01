package com.foreignerwarsaw.common.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Kills every active session for a principal. Works because Spring Session JDBC indexes sessions by
 * principal name automatically as soon as an {@code Authentication} is stored in the session (which
 * is exactly what session-based login does) - no extra wiring needed beyond the {@link
 * FindByIndexNameSessionRepository} Spring Boot already auto-configures for a JDBC-backed session
 * store.
 */
@Component
public class SessionInvalidator {

  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  public SessionInvalidator(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  /**
   * Used by password reset (brief §15, mandatory) and change-password (brief §16 - documented
   * choice: invalidate every session, including the caller's, rather than the more complex "keep
   * this one" variant, which the brief explicitly allows at MVP stage).
   */
  public void invalidateAllSessionsFor(String principalName) {
    sessionRepository
        .findByPrincipalName(principalName)
        .keySet()
        .forEach(sessionRepository::deleteById);
  }
}
