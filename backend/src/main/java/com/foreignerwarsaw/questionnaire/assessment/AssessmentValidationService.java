package com.foreignerwarsaw.questionnaire.assessment;

import com.foreignerwarsaw.common.web.ApiError;
import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.questionnaire.assessment.dto.AnswerRequest;
import com.foreignerwarsaw.questionnaire.option.QuestionOption;
import com.foreignerwarsaw.questionnaire.option.QuestionOptionRepository;
import com.foreignerwarsaw.questionnaire.question.QuestionType;
import com.foreignerwarsaw.questionnaire.question.QuestionnaireQuestion;
import com.foreignerwarsaw.reference.country.CountryRepository;
import com.foreignerwarsaw.reference.geography.CityRepository;
import com.foreignerwarsaw.reference.geography.DistrictRepository;
import com.foreignerwarsaw.reference.geography.RegionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Typed validation of one incoming {@link AnswerRequest} against the {@link QuestionnaireQuestion}
 * it targets (brief §27/§61) - type match, allowed option, reference existence, and the handful of
 * generic data-quality bounds the brief names explicitly (salary non-negative, date of birth not in
 * the future). Deliberately NOT legal validation (brief §27's own limit) - nothing here decides
 * eligibility, only whether the answer is a well-formed instance of its declared type.
 */
@Service
public class AssessmentValidationService {

  private final QuestionOptionRepository questionOptionRepository;
  private final CountryRepository countryRepository;
  private final RegionRepository regionRepository;
  private final CityRepository cityRepository;
  private final DistrictRepository districtRepository;
  private final Clock clock;

  public AssessmentValidationService(
      QuestionOptionRepository questionOptionRepository,
      CountryRepository countryRepository,
      RegionRepository regionRepository,
      CityRepository cityRepository,
      DistrictRepository districtRepository,
      Clock clock) {
    this.questionOptionRepository = questionOptionRepository;
    this.countryRepository = countryRepository;
    this.regionRepository = regionRepository;
    this.cityRepository = cityRepository;
    this.districtRepository = districtRepository;
    this.clock = clock;
  }

  public void validate(QuestionnaireQuestion questionnaireQuestion, AnswerRequest request) {
    String fieldName = questionnaireQuestion.getQuestion().getCode();

    if (request.unsure()) {
      if (!questionnaireQuestion.isAllowUnsure()) {
        throw fieldError(fieldName, "This question does not accept \"not sure\" as an answer.");
      }
      return;
    }

    QuestionType type = questionnaireQuestion.getQuestion().getQuestionType();
    switch (type) {
      case BOOLEAN ->
          requireNonNull(request.booleanValue(), fieldName, "A yes/no value is required.");
      case TEXT -> requireNonBlank(request.stringValue(), fieldName, "Text is required.");
      case INTEGER ->
          requireNonNull(request.integerValue(), fieldName, "A whole number is required.");
      case DECIMAL -> validateDecimal(questionnaireQuestion, request, fieldName);
      case DATE -> validateDate(questionnaireQuestion, request, fieldName);
      case COUNTRY ->
          validateReference(
              request.referenceCode(),
              fieldName,
              "country",
              code -> countryRepository.findByCodeIgnoreCase(code).isPresent());
      case REGION ->
          validateReference(
              request.referenceCode(),
              fieldName,
              "region",
              code -> regionRepository.findByCodeIgnoreCase(code).isPresent());
      case CITY ->
          validateReference(
              request.referenceCode(),
              fieldName,
              "city",
              code -> cityRepository.findByCodeIgnoreCase(code).isPresent());
      case DISTRICT ->
          validateReference(
              request.referenceCode(),
              fieldName,
              "district",
              code -> districtRepository.findByCodeIgnoreCase(code).isPresent());
      case SINGLE_SELECT -> validateSingleSelect(questionnaireQuestion, request, fieldName);
      case MULTI_SELECT -> validateMultiSelect(questionnaireQuestion, request, fieldName);
    }
  }

  private void validateDecimal(
      QuestionnaireQuestion questionnaireQuestion, AnswerRequest request, String fieldName) {
    BigDecimal value = request.decimalValue();
    requireNonNull(value, fieldName, "A number is required.");
    if (value.signum() < 0) {
      throw fieldError(fieldName, "Value must be greater than or equal to zero.");
    }
  }

  private void validateDate(
      QuestionnaireQuestion questionnaireQuestion, AnswerRequest request, String fieldName) {
    LocalDate value = request.dateValue();
    requireNonNull(value, fieldName, "A date is required.");
    if ("DATE_OF_BIRTH".equals(fieldName) && !value.isBefore(LocalDate.now(clock))) {
      throw fieldError(fieldName, "Date of birth cannot be in the future.");
    }
  }

  private void validateReference(
      String referenceCode, String fieldName, String kind, Predicate<String> exists) {
    requireNonBlank(referenceCode, fieldName, "A " + kind + " must be selected.");
    if (!exists.test(referenceCode)) {
      throw fieldError(fieldName, "Unknown " + kind + " code: " + referenceCode);
    }
  }

  private void validateSingleSelect(
      QuestionnaireQuestion questionnaireQuestion, AnswerRequest request, String fieldName) {
    requireNonBlank(request.referenceCode(), fieldName, "An option must be selected.");
    Set<String> validCodes = activeOptionCodes(questionnaireQuestion);
    if (!validCodes.contains(request.referenceCode())) {
      throw fieldError(fieldName, "\"" + request.referenceCode() + "\" is not a valid option.");
    }
  }

  private void validateMultiSelect(
      QuestionnaireQuestion questionnaireQuestion, AnswerRequest request, String fieldName) {
    List<String> selected = request.selectedOptionCodes();
    if (selected == null || selected.isEmpty()) {
      throw fieldError(fieldName, "At least one option must be selected.");
    }
    Set<String> validCodes = activeOptionCodes(questionnaireQuestion);
    for (String code : selected) {
      if (!validCodes.contains(code)) {
        throw fieldError(fieldName, "\"" + code + "\" is not a valid option.");
      }
    }
  }

  private Set<String> activeOptionCodes(QuestionnaireQuestion questionnaireQuestion) {
    return questionOptionRepository
        .findByQuestionnaireQuestion_IdOrderBySortOrder(questionnaireQuestion.getId())
        .stream()
        .filter(QuestionOption::isActive)
        .map(QuestionOption::getCode)
        .collect(Collectors.toSet());
  }

  private void requireNonNull(Object value, String fieldName, String message) {
    if (value == null) {
      throw fieldError(fieldName, message);
    }
  }

  private void requireNonBlank(String value, String fieldName, String message) {
    if (value == null || value.isBlank()) {
      throw fieldError(fieldName, message);
    }
  }

  private ApiException fieldError(String fieldName, String message) {
    return new ApiException(
        HttpStatus.BAD_REQUEST,
        "INVALID_ASSESSMENT_ANSWER",
        "Request validation failed",
        List.of(new ApiError.FieldViolation(fieldName, message)));
  }
}
