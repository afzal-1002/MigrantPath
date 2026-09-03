package com.foreignerwarsaw.questionnaire.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateQuestionnaireDraftRequest(@NotBlank String title, String description) {}
