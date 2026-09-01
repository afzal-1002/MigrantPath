package com.foreignerwarsaw.email;

import com.foreignerwarsaw.config.MailProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Thin, generic sending abstraction (brief §24) - {@link VerificationEmailService} and {@link
 * PasswordResetEmailService} build the specific subject/body and call this. Captured by Mailpit in
 * local/test (docker-compose.yml); never logs message content (which would include the
 * verification/reset URL - brief §39 explicitly warns against exposing raw tokens in logs, and the
 * URL contains one).
 */
@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private final JavaMailSender mailSender;
  private final MailProperties mailProperties;

  public EmailService(JavaMailSender mailSender, MailProperties mailProperties) {
    this.mailSender = mailSender;
    this.mailProperties = mailProperties;
  }

  /**
   * For MVP, registration/reset flows commit their database changes first and send email afterward
   * (brief §33) - a failed send here is caught and logged, not rethrown, so it never rolls back an
   * already-committed registration. The user can always request a new email (resend-verification /
   * forgot-password) if the first one didn't arrive.
   */
  public void send(String to, String subject, String htmlBody) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
      helper.setFrom(mailProperties.fromAddress());
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(htmlBody, true);
      mailSender.send(message);
    } catch (Exception e) {
      log.warn("Failed to send email (subject={}): {}", subject, e.getMessage());
    }
  }
}
