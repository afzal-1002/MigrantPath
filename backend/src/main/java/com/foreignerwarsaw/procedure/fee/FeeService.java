package com.foreignerwarsaw.procedure.fee;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.procedure.PublicationStatus;
import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeeService {

  private final FeeRepository feeRepository;
  private final FeeVersionRepository feeVersionRepository;

  public FeeService(FeeRepository feeRepository, FeeVersionRepository feeVersionRepository) {
    this.feeRepository = feeRepository;
    this.feeVersionRepository = feeVersionRepository;
  }

  @Transactional
  public FeeVersion addFee(
      ProcedureVersion procedureVersion,
      String stableCode,
      FeeType feeType,
      BigDecimal amount,
      String currency) {
    if (procedureVersion.getStatus() != PublicationStatus.DRAFT) {
      throw new ApiException(
          HttpStatus.CONFLICT, "VERSION_NOT_DRAFT", "Fees can only be added to a DRAFT version");
    }
    if (amount.signum() < 0) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_FEE_AMOUNT", "Fee amount must not be negative");
    }
    Procedure procedure = procedureVersion.getProcedure();
    Fee fee =
        feeRepository
            .findByProcedure_IdAndStableCode(procedure.getId(), stableCode)
            .orElseGet(() -> feeRepository.save(new Fee(procedure, stableCode, feeType)));
    FeeVersion feeVersion = new FeeVersion(fee, procedureVersion, amount, currency);
    return feeVersionRepository.save(feeVersion);
  }

  @Transactional(readOnly = true)
  public List<FeeVersion> listForVersion(UUID procedureVersionId) {
    return feeVersionRepository.findByProcedureVersion_Id(procedureVersionId);
  }

  /** Phase 9 addition (brief §25) - editing a fee already on a still-DRAFT version. */
  @Transactional
  public FeeVersion updateFee(
      UUID feeVersionId,
      BigDecimal amount,
      String currency,
      String description,
      String paymentInstructions,
      Boolean refundable) {
    if (amount.signum() < 0) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_FEE_AMOUNT", "Fee amount must not be negative");
    }
    FeeVersion fee = getManagedById(feeVersionId);
    requireDraft(fee.getProcedureVersion());
    fee.setAmount(amount);
    fee.setCurrency(currency);
    fee.setDescription(description);
    fee.setPaymentInstructions(paymentInstructions);
    fee.setRefundable(refundable);
    return fee;
  }

  /** Phase 9 addition (brief §25) - removing a fee from a still-DRAFT version. */
  @Transactional
  public void removeFee(UUID feeVersionId) {
    FeeVersion fee = getManagedById(feeVersionId);
    requireDraft(fee.getProcedureVersion());
    feeVersionRepository.delete(fee);
  }

  private FeeVersion getManagedById(UUID id) {
    return feeVersionRepository
        .findByIdFetchingAll(id)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND, "FEE_NOT_FOUND", "No fee found for id " + id));
  }

  private void requireDraft(ProcedureVersion procedureVersion) {
    if (procedureVersion.getStatus() != PublicationStatus.DRAFT) {
      throw new ApiException(
          HttpStatus.CONFLICT, "VERSION_NOT_DRAFT", "Fees can only be edited on a DRAFT version");
    }
  }
}
