package com.foreignerwarsaw.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Shared by email verification and password reset (brief §34): 32 bytes of {@link SecureRandom}
 * output (not {@code UUID.randomUUID()}, {@code Math.random()}, or a timestamp/predictable hash),
 * base64url-encoded without padding for the raw token that goes in the email link, and its SHA-256
 * hex digest for what's actually persisted (V3__create_auth_tokens.sql) - the raw value is never
 * written to the database or a log line anywhere in this codebase.
 */
@Component
public class TokenGenerator {

  private static final int TOKEN_BYTES = 32;
  private final SecureRandom secureRandom = new SecureRandom();

  public record GeneratedToken(String rawToken, String tokenHash) {}

  public GeneratedToken generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new GeneratedToken(rawToken, hash(rawToken));
  }

  public String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is a mandatory JDK algorithm (JLS platform guarantee) - this can only
      // happen on a broken JVM, not from any user/runtime input.
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
