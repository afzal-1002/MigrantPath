package com.foreignerwarsaw.usercase.engine.dto;

import com.foreignerwarsaw.usercase.engine.CaseProgress;

public record CaseProgressResponse(
    int stepsCompleted,
    int stepsTotal,
    int documentsReady,
    int documentsTotal,
    int conditionalDocumentsToReview) {

  public static CaseProgressResponse from(CaseProgress progress) {
    return new CaseProgressResponse(
        progress.stepsCompleted(),
        progress.stepsTotal(),
        progress.documentsReady(),
        progress.documentsTotal(),
        progress.conditionalDocumentsToReview());
  }
}
