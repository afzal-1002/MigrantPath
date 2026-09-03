package com.foreignerwarsaw.rules.admin;

import com.foreignerwarsaw.rules.admin.dto.FactResponse;
import com.foreignerwarsaw.rules.evaluation.FactRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backs the admin Rule condition builder's fact dropdown (brief §38) - every fact a condition may
 * legally reference, with its value type and allowed operators, so the builder never lets an author
 * type an unknown fact code or pick an operator {@link
 * com.foreignerwarsaw.rules.condition.ConditionTreeValidator} would reject anyway.
 */
@RestController
@RequestMapping("/api/v1/admin/facts")
@Tag(name = "Admin - Rules")
public class AdminFactController {

  private final FactRegistry factRegistry;

  public AdminFactController(FactRegistry factRegistry) {
    this.factRegistry = factRegistry;
  }

  @Operation(summary = "Every fact a condition tree may reference, with its allowed operators")
  @GetMapping
  public List<FactResponse> list() {
    return factRegistry.listAll().stream().map(FactResponse::from).toList();
  }
}
