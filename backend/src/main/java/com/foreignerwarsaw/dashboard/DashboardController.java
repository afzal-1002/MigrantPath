package com.foreignerwarsaw.dashboard;

import com.foreignerwarsaw.dashboard.dto.DashboardResponse;
import com.foreignerwarsaw.user.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Post-MVP UX Milestone UX1 (redesign pass) - the single dashboard aggregation endpoint (brief
 * §27-§34: prefer one endpoint over several client-side calls). Current authenticated user only -
 * no {@code userId} path/query parameter exists, exactly like {@code UserCaseController} (brief
 * §55: privacy first, no admin/support access to another user's dashboard).
 */
@RestController
@Tag(name = "Dashboard")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @Operation(
      summary =
          "The caller's own dashboard summary: primary case, next actions, important dates,"
              + " completed milestones, latest recommendations and recent activity (brief §27-§34)")
  @GetMapping("/api/v1/dashboard")
  public DashboardResponse getDashboard(@AuthenticationPrincipal AppUserPrincipal principal) {
    return dashboardService.getDashboard(principal.getUserId());
  }
}
