package com.foreignerwarsaw.auth;

/**
 * Mirrors the CHECK constraint on {@code user_consents.consent_type}
 * (V4__create_user_consents.sql). {@code MARKETING_EMAILS} is never recorded by registration - a
 * row for it would only ever be created by a future, explicit opt-in action (brief §4).
 */
public enum ConsentType {
  TERMS_OF_SERVICE,
  PRIVACY_POLICY,
  MARKETING_EMAILS
}
