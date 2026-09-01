package com.foreignerwarsaw.user;

import com.foreignerwarsaw.user.dto.ChangePasswordRequest;
import com.foreignerwarsaw.user.dto.CurrentUserResponse;
import com.foreignerwarsaw.user.dto.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint here requires an authenticated session - see SecurityConfig's {@code
 * .anyRequest().authenticated()} rule; there is no per-method {@code @PreAuthorize} needed for
 * "must be logged in," only for role checks a later phase introduces.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

  private final UserAccountService userAccountService;

  public UserController(UserAccountService userAccountService) {
    this.userAccountService = userAccountService;
  }

  @Operation(summary = "The authenticated user's own profile")
  @GetMapping("/me")
  public ResponseEntity<CurrentUserResponse> me(
      @AuthenticationPrincipal AppUserPrincipal principal) {
    return ResponseEntity.ok(CurrentUserResponse.from(loadedUser(principal)));
  }

  @Operation(
      summary =
          "Update first name / preferred language (immigration profile fields arrive in a later phase)")
  @PatchMapping("/me")
  public ResponseEntity<CurrentUserResponse> updateProfile(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @Valid @RequestBody UpdateProfileRequest request) {
    User updated = userAccountService.updateProfile(principal.getUserId(), request);
    return ResponseEntity.ok(CurrentUserResponse.from(updated));
  }

  @Operation(summary = "Change password (requires current password; invalidates all sessions)")
  @PostMapping("/me/change-password")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @Valid @RequestBody ChangePasswordRequest request) {
    userAccountService.changePassword(principal.getUserId(), request);
    return ResponseEntity.noContent().build();
  }

  private User loadedUser(AppUserPrincipal principal) {
    // A thin re-fetch so /me reflects the current DB state (e.g. a profile field
    // changed in another request) rather than whatever was true when the session's
    // principal was originally loaded at login time.
    return userAccountService.getById(principal.getUserId());
  }
}
