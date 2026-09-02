package com.foreignerwarsaw.procedure.admin.dto;

import com.foreignerwarsaw.procedure.source.SourceRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AttachSourceRequest(@NotNull UUID officialSourceId, @NotNull SourceRole role) {}
