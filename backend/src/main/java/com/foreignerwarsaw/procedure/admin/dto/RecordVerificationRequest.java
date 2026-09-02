package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.source.VerificationStatus;
import jakarta.validation.constraints.NotNull;

public record RecordVerificationRequest(@NotNull VerificationStatus status, String notes) {}
