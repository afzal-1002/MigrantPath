package com.foreignerwarsaw.admin.users;

import com.foreignerwarsaw.admin.users.dto.AdminUserResponse;
import com.foreignerwarsaw.admin.users.dto.AssignRoleRequest;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9's role-management surface (brief §81-§83) - ADMIN-only (enforced by {@code
 * SecurityConfig}'s {@code /api/v1/admin/users/**} matcher). Deliberately minimal: search by email,
 * view roles, assign/remove an administrative role - never a full user-surveillance profile browser
 * (brief §81's explicit boundary). No Assessment/Recommendation/UserCase content is ever reachable
 * from here (brief §83/§133).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin - Users")
public class AdminUserController {

  private final AdminUserService adminUserService;
  private final UserAccountService userAccountService;

  public AdminUserController(
      AdminUserService adminUserService, UserAccountService userAccountService) {
    this.adminUserService = adminUserService;
    this.userAccountService = userAccountService;
  }

  @Operation(summary = "Search accounts by email fragment")
  @GetMapping
  public List<AdminUserResponse> search(@RequestParam(required = false) String email) {
    return adminUserService.search(email).stream().map(AdminUserResponse::from).toList();
  }

  @Operation(summary = "One account's roles/status")
  @GetMapping("/{id}")
  public AdminUserResponse detail(@PathVariable UUID id) {
    return AdminUserResponse.from(adminUserService.getById(id));
  }

  @Operation(summary = "Assign an administrative role")
  @PostMapping("/{id}/roles")
  public AdminUserResponse assignRole(
      @PathVariable UUID id,
      @Valid @RequestBody AssignRoleRequest request,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    return AdminUserResponse.from(
        adminUserService.assignRole(id, request.roleCode(), actor(principal)));
  }

  @Operation(summary = "Remove an administrative role (cannot remove your own last ADMIN role)")
  @DeleteMapping("/{id}/roles/{roleCode}")
  public AdminUserResponse removeRole(
      @PathVariable UUID id,
      @PathVariable String roleCode,
      @AuthenticationPrincipal AppUserPrincipal principal) {
    return AdminUserResponse.from(adminUserService.removeRole(id, roleCode, actor(principal)));
  }

  private User actor(AppUserPrincipal principal) {
    return userAccountService.getById(principal.getUserId());
  }
}
