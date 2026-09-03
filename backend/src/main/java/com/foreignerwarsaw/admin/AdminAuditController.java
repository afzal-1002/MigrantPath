package com.foreignerwarsaw.admin;

import com.foreignerwarsaw.admin.dto.AuditLogResponse;
import com.foreignerwarsaw.common.audit.AuditActionType;
import com.foreignerwarsaw.common.audit.AuditEntityType;
import com.foreignerwarsaw.common.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 9's audit trail admin page backend (brief §60-§64) - ADMIN-only (enforced by {@code
 * SecurityConfig}'s {@code /api/v1/admin/audit/**} matcher). Read-only, paginated, filtered - never
 * an arbitrary SQL-like query surface (brief §64).
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@Tag(name = "Admin - Audit")
public class AdminAuditController {

  private final AuditService auditService;

  public AdminAuditController(AuditService auditService) {
    this.auditService = auditService;
  }

  @Operation(summary = "Search the audit log")
  @GetMapping
  public Page<AuditLogResponse> search(
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) AuditActionType actionType,
      @RequestParam(required = false) AuditEntityType entityType,
      @RequestParam(required = false) String entityBusinessCode,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    Page<com.foreignerwarsaw.common.audit.AuditLog> result =
        auditService.search(
            actorId,
            actionType,
            entityType,
            entityBusinessCode,
            from,
            to,
            PageRequest.of(page, Math.min(size, 200), Sort.unsorted()));
    return result.map(AuditLogResponse::from);
  }
}
