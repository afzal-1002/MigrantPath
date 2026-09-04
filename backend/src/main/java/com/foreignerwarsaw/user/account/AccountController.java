package com.foreignerwarsaw.user.account;

import com.foreignerwarsaw.common.web.ApiException;
import com.foreignerwarsaw.user.AppUserPrincipal;
import com.foreignerwarsaw.user.account.dto.AccountExportResponse;
import com.foreignerwarsaw.user.account.dto.DeleteAccountRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canonical Phase 12 (Security/Privacy/GDPR) self-service privacy endpoints (brief §9/§17/§33/
 * §74). Every endpoint here operates only on the current authenticated principal - never a {@code
 * userId} path/body parameter (brief §4/§74: "no endpoint accepts arbitrary userId for normal user
 * export/deletion") - so there is no admin-bypass surface and no cross-user IDOR shape to even test
 * for.
 */
@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account privacy")
public class AccountController {

  private final AccountExportService exportService;
  private final AccountDeletionService deletionService;

  public AccountController(
      AccountExportService exportService, AccountDeletionService deletionService) {
    this.exportService = exportService;
    this.deletionService = deletionService;
  }

  @Operation(
      summary =
          "Download all personal data associated with the authenticated account, as JSON (brief §16)")
  @GetMapping("/export")
  public ResponseEntity<AccountExportResponse> export(
      @AuthenticationPrincipal AppUserPrincipal principal) {
    AccountExportResponse body = exportService.exportOwnData(principal.getUserId());
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("account-export.json").build().toString())
        .cacheControl(CacheControl.noStore())
        .body(body);
  }

  @Operation(
      summary =
          "Permanently delete the authenticated account and its personal data (brief §17/§28); requires current-password reauthentication")
  @PostMapping("/delete")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @Valid @RequestBody DeleteAccountRequest request) {
    if (!"DELETE".equals(request.confirmation())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "ACCOUNT_DELETION_CONFIRMATION_REQUIRED",
          "confirmation must be the literal value \"DELETE\"");
    }
    deletionService.deleteOwnAccount(principal.getUserId(), request.currentPassword());
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
