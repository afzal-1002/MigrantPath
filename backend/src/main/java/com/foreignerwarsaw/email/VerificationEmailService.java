package com.foreignerwarsaw.email;

import com.foreignerwarsaw.config.AuthProperties;
import com.foreignerwarsaw.observability.EmailMetrics;
import org.springframework.stereotype.Service;

@Service
public class VerificationEmailService {

  private final EmailService emailService;
  private final AuthProperties authProperties;

  public VerificationEmailService(EmailService emailService, AuthProperties authProperties) {
    this.emailService = emailService;
    this.authProperties = authProperties;
  }

  public void send(String toEmail, String rawToken) {
    // Frontend URL, never hard-coded (brief §24) - see app.auth.frontend-base-url.
    String link = authProperties.frontendBaseUrl() + "/verify-email?token=" + rawToken;
    String body =
        """
        <p>Welcome to Foreigner Warsaw.</p>
        <p>Please confirm your email address to activate your account:</p>
        <p><a href="%s">Verify my email</a></p>
        <p>This link expires in %d hours. If you didn't create this account, you can ignore this email.</p>
        """
            .formatted(link, authProperties.emailVerificationTokenTtl().toHours());
    emailService.send(
        toEmail, "Verify your Foreigner Warsaw account", body, EmailMetrics.Type.VERIFICATION);
  }
}
