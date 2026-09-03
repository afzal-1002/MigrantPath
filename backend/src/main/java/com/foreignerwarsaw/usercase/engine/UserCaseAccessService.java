package com.foreignerwarsaw.usercase.engine;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.usercase.core.UserCase;
import com.foreignerwarsaw.usercase.core.UserCaseRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one ownership check every case-scoped service/endpoint uses (brief §54/§55/§107) - a 404,
 * never a 403, for another user's case, same IDOR discipline as {@code AssessmentService#getOwned}
 * and every other owned resource in this codebase. {@code CONTENT_EDITOR}/{@code
 * LEGAL_REVIEWER}/{@code ADMIN} gain no special access here - a case is private to its owner (brief
 * §55).
 */
@Service
public class UserCaseAccessService {

  private final UserCaseRepository userCaseRepository;

  public UserCaseAccessService(UserCaseRepository userCaseRepository) {
    this.userCaseRepository = userCaseRepository;
  }

  @Transactional(readOnly = true)
  public UserCase getOwned(UUID caseId, UUID userId) {
    UserCase userCase =
        userCaseRepository
            .findByIdFetchingProcedureAndRevision(caseId)
            .orElseThrow(() -> notFound(caseId));
    if (!userCase.getUser().getId().equals(userId)) {
      throw notFound(caseId);
    }
    return userCase;
  }

  private ApiException notFound(UUID caseId) {
    return new ApiException(
        HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "No case found for id " + caseId);
  }
}
