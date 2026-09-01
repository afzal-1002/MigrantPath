package com.foreignerwarsaw.auth;

import com.foreignerwarsaw.auth.dto.ForgotPasswordRequest;
import com.foreignerwarsaw.auth.dto.LoginRequest;
import com.foreignerwarsaw.auth.dto.MessageResponse;
import com.foreignerwarsaw.auth.dto.RegisterRequest;
import com.foreignerwarsaw.auth.dto.ResendVerificationRequest;
import com.foreignerwarsaw.auth.dto.ResetPasswordRequest;
import com.foreignerwarsaw.auth.dto.VerifyEmailRequest;
import com.foreignerwarsaw.user.User;
import com.foreignerwarsaw.user.dto.CurrentUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/auth/logout} is not a method here - it's handled declaratively by Spring
 * Security's logout filter (see SecurityConfig), which correctly enforces CSRF on it and
 * invalidates the session without needing hand-written controller code.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

  private static final String GENERIC_VERIFICATION_MESSAGE =
      "If an account exists for this email and is not yet verified, a new verification link has been sent.";
  private static final String GENERIC_FORGOT_PASSWORD_MESSAGE =
      "If an account exists for this email, password reset instructions have been sent.";

  private final RegistrationService registrationService;
  private final EmailVerificationService emailVerificationService;
  private final PasswordResetService passwordResetService;
  private final LoginService loginService;

  public AuthController(
      RegistrationService registrationService,
      EmailVerificationService emailVerificationService,
      PasswordResetService passwordResetService,
      LoginService loginService) {
    this.registrationService = registrationService;
    this.emailVerificationService = emailVerificationService;
    this.passwordResetService = passwordResetService;
    this.loginService = loginService;
  }

  @Operation(
      summary = "Register a new account (starts PENDING_VERIFICATION; sends a verification email)")
  @PostMapping("/register")
  public ResponseEntity<CurrentUserResponse> register(@Valid @RequestBody RegisterRequest request) {
    User user =
        registrationService.registerAndSendVerificationEmail(
            request.email(),
            request.password(),
            request.firstName(),
            request.acceptTerms(),
            request.acceptPrivacyPolicy());
    return ResponseEntity.status(HttpStatus.CREATED).body(CurrentUserResponse.from(user));
  }

  @Operation(
      summary = "Verify an account's email address using the token from the verification email")
  @PostMapping("/verify-email")
  public ResponseEntity<MessageResponse> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest request) {
    emailVerificationService.verify(request.token());
    return ResponseEntity.ok(new MessageResponse("Email verified. You can now sign in."));
  }

  @Operation(summary = "Request a new verification email (rate-limited, generic response)")
  @PostMapping("/resend-verification")
  public ResponseEntity<MessageResponse> resendVerification(
      @Valid @RequestBody ResendVerificationRequest request) {
    emailVerificationService.resend(request.email());
    return ResponseEntity.ok(new MessageResponse(GENERIC_VERIFICATION_MESSAGE));
  }

  @Operation(summary = "Sign in, establishing a server-side session (CSRF token required)")
  @PostMapping("/login")
  public ResponseEntity<CurrentUserResponse> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    User user = loginService.login(request.email(), request.password(), httpRequest, httpResponse);
    return ResponseEntity.ok(CurrentUserResponse.from(user));
  }

  @Operation(
      summary =
          "Request a password reset email (rate-limited, generic response - never reveals account existence)")
  @PostMapping("/forgot-password")
  public ResponseEntity<MessageResponse> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request) {
    passwordResetService.forgotPassword(request.email());
    return ResponseEntity.ok(new MessageResponse(GENERIC_FORGOT_PASSWORD_MESSAGE));
  }

  @Operation(
      summary =
          "Reset a password using the token from the reset email; invalidates the user's sessions")
  @PostMapping("/reset-password")
  public ResponseEntity<MessageResponse> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request) {
    passwordResetService.resetPassword(request.token(), request.newPassword());
    return ResponseEntity.ok(
        new MessageResponse("Password updated. Please sign in with your new password."));
  }
}
