package com.foreignerwarsaw.admin.users;

import com.foreignerwarsaw.common.audit.AuditActionType;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.common.audit.AuditService;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.user.Role;
import com.foreignerwarsaw.user.RoleRepository;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin role management (brief §81/§82) - the only place a role is ever assigned/removed, and the
 * only place role-escalation safety is enforced. Only ADMIN can reach these endpoints at all
 * (enforced by {@code SecurityConfig}'s {@code /api/v1/admin/users/**} matcher), which alone
 * satisfies brief §82's "only ADMIN can assign CONTENT_EDITOR/LEGAL_REVIEWER/ADMIN" - this class
 * additionally guards the one scenario role-based authorization alone can't prevent: an ADMIN
 * removing their own last remaining ADMIN role, which would otherwise permanently lock every admin
 * out (brief §82).
 */
@Service
public class AdminUserService {

  private static final String ADMIN_ROLE_CODE = "ADMIN";

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final AuditService auditService;

  public AdminUserService(
      UserRepository userRepository, RoleRepository roleRepository, AuditService auditService) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<User> search(String emailFragment) {
    if (emailFragment == null || emailFragment.isBlank()) {
      return userRepository.findAll();
    }
    return userRepository.findByEmailContainingIgnoreCase(emailFragment);
  }

  @Transactional(readOnly = true)
  public User getById(UUID id) {
    return userRepository
        .findById(id)
        .orElseThrow(
            () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "No user found"));
  }

  @Transactional
  public User assignRole(UUID userId, String roleCode, User actor) {
    User user = getManagedById(userId);
    Role role = getRole(roleCode);
    user.addRole(role);
    auditService.record(
        actor,
        AuditActionType.ROLE_ASSIGNED,
        AuditEntityType.USER,
        user.getId(),
        user.getEmail(),
        null,
        "Assigned role " + roleCode + " to " + user.getEmail());
    return user;
  }

  @Transactional
  public User removeRole(UUID userId, String roleCode, User actor) {
    User user = getManagedById(userId);
    Role role = getRole(roleCode);
    if (ADMIN_ROLE_CODE.equals(roleCode)
        && user.getId().equals(actor.getId())
        && user.getRoles().stream().filter(r -> ADMIN_ROLE_CODE.equals(r.getCode())).count() <= 1) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CANNOT_REMOVE_OWN_LAST_ADMIN_ROLE",
          "You cannot remove your own last ADMIN role");
    }
    user.removeRole(role);
    auditService.record(
        actor,
        AuditActionType.ROLE_REMOVED,
        AuditEntityType.USER,
        user.getId(),
        user.getEmail(),
        null,
        "Removed role " + roleCode + " from " + user.getEmail());
    return user;
  }

  private User getManagedById(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(
            () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "No user found"));
  }

  private Role getRole(String code) {
    return roleRepository
        .findByCode(code)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE", "Unknown role code: " + code));
  }
}
