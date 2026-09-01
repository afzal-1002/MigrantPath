package com.foreignerwarsaw.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Minimal security-event logging foundation (brief §23). A formal, queryable {@code AuditLog} table
 * (docs/database/DATABASE.md §9) is Phase 9's admin-audit concern; this only guarantees the
 * *events* are captured, structured, and safe (no passwords/tokens/full user objects - user
 * identity is the user ID, never the email, to keep PII out of log aggregators by default) from day
 * one, so nothing has to be retrofitted later.
 */
@Component
public class SecurityEventLogger {

  private static final Logger log = LoggerFactory.getLogger("SECURITY_EVENT");

  public enum Event {
    USER_REGISTERED,
    EMAIL_VERIFIED,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    PASSWORD_CHANGED,
    ACCOUNT_LOCKED
  }

  public void log(Event event, String userId) {
    MDC.put("securityEvent", event.name());
    try {
      log.info("event={} userId={}", event, userId);
    } finally {
      MDC.remove("securityEvent");
    }
  }

  /**
   * For events where no account is known to exist yet (e.g. a login attempt against an unrecognized
   * email) - deliberately never logs the email/identifier itself, since that's exactly the kind of
   * value that turns a log file into an account- enumeration oracle.
   */
  public void logUnknownSubject(Event event) {
    log.info("event={} userId=unknown", event);
  }
}
