package com.foreignerwarsaw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code spring.mail.*} configures the SMTP transport itself; this is the one extra value (the From
 * address) Spring Boot doesn't already bind for us.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String fromAddress) {}
