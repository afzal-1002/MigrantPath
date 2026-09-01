package com.foreignerwarsaw.user.dto;

import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.User;
import java.util.List;
import java.util.UUID;

/**
 * Deliberately excludes password hash, tokens, session identifiers, and internal audit fields
 * (brief §13) - only what a client legitimately needs.
 */
public record CurrentUserResponse(
    UUID id,
    String email,
    String firstName,
    String preferredLanguage,
    boolean emailVerified,
    List<String> roles) {

  public static CurrentUserResponse from(User user) {
    return new CurrentUserResponse(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getPreferredLanguage(),
        user.isEmailVerified(),
        user.getRoles().stream().map(Role::getCode).sorted().toList());
  }
}
