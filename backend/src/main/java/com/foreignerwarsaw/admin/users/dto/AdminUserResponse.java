package com.foreignerwarsaw.admin.users.dto;

import com.foreignerwarsaw.user.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deliberately account-management fields only (brief §81/§83): email, status, roles, verification,
 * created date - never Assessments, salary, citizenship, family details, or UserCases.
 */
public record AdminUserResponse(
    UUID id,
    String email,
    String status,
    boolean emailVerified,
    Instant createdAt,
    List<String> roles) {

  public static AdminUserResponse from(User user) {
    return new AdminUserResponse(
        user.getId(),
        user.getEmail(),
        user.getStatus().name(),
        user.isEmailVerified(),
        user.getCreatedAt(),
        user.getRoles().stream().map(com.foreignerwarsaw.user.Role::getCode).sorted().toList());
  }
}
