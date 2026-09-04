package com.foreignerwarsaw.email;

import com.foreignerwarsaw.config.AuthProperties;
import com.foreignerwarsaw.observability.EmailMetrics;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailService {

  private final EmailService emailService;
  private final AuthProperties authProperties;

  public PasswordResetEmailService(EmailService emailService, AuthProperties authProperties) {
    this.emailService = emailService;
    this.authProperties = authProperties;
  }

  public void send(String toEmail, String rawToken) {
    String link = authProperties.frontendBaseUrl() + "/reset-password?token=" + rawToken;
    String body =
        """
        <p>We received a request to reset your Foreigner Warsaw password.</p>
        <p><a href="%s">Reset my password</a></p>
        <p>This link expires in %d minutes. If you didn't request this, you can ignore this
        email - your password will not be changed.</p>
        """
            .formatted(link, authProperties.passwordResetTokenTtl().toMinutes());
    emailService.send(
        toEmail, "Reset your Foreigner Warsaw password", body, EmailMetrics.Type.PASSWORD_RESET);
  }
}
