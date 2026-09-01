package com.foreignerwarsaw.user;

import com.foreignerwarsaw.common.security.SecurityEventLogger;
import com.foreignerwarsaw.common.security.SessionInvalidator;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.user.dto.ChangePasswordRequest;
import com.foreignerwarsaw.user.dto.UpdateProfileRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Phase 2 profile scope only (brief §30) - see {@link UpdateProfileRequest}. */
@Service
public class UserAccountService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionInvalidator sessionInvalidator;
  private final SecurityEventLogger securityEventLogger;

  public UserAccountService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      SessionInvalidator sessionInvalidator,
      SecurityEventLogger securityEventLogger) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.sessionInvalidator = sessionInvalidator;
    this.securityEventLogger = securityEventLogger;
  }

  @Transactional(readOnly = true)
  public User getById(UUID userId) {
    return requireUser(userId);
  }

  @Transactional
  public User updateProfile(UUID userId, UpdateProfileRequest request) {
    User user = requireUser(userId);
    if (request.firstName() != null) {
      user.setFirstName(request.firstName());
    }
    if (request.preferredLanguage() != null) {
      user.setPreferredLanguage(request.preferredLanguage());
    }
    return user;
  }

  /**
   * Requires the current password (brief §16). Documented choice: invalidates *every* session for
   * the user, including the one making this request - the brief explicitly allows this simpler
   * behavior over the more complex "keep the current session alive" variant at MVP stage. The
   * caller re-authenticates on their next request either way, since their own session cookie no
   * longer resolves to anything.
   */
  @Transactional
  public void changePassword(UUID userId, ChangePasswordRequest request) {
    User user = requireUser(userId);
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Current password is incorrect");
    }
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    sessionInvalidator.invalidateAllSessionsFor(user.getEmail());
    securityEventLogger.log(SecurityEventLogger.Event.PASSWORD_CHANGED, user.getId().toString());
  }

  private User requireUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(
            () -> new IllegalStateException("Authenticated principal has no matching user row"));
  }
}
