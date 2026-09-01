package com.foreignerwarsaw.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password policy (brief §6): minimum meaningful length (10) and a generous maximum (128, just to
 * bound input size - bcrypt itself silently truncates far beyond this, so 128 is about preventing
 * abuse, not a "safety" limit); no composition rules (uppercase/digit/symbol) that a long,
 * password-manager-generated passphrase would fail for no real security benefit and that push users
 * toward predictable patterns instead.
 */
public record RegisterRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 10, max = 128) String password,
    String firstName,
    @AssertTrue(message = "Terms of Service must be accepted") boolean acceptTerms,
    @AssertTrue(message = "Privacy Policy must be accepted") boolean acceptPrivacyPolicy) {}
