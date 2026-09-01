package com.foreignerwarsaw.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank String currentPassword, @NotBlank @Size(min = 10, max = 128) String newPassword) {}
