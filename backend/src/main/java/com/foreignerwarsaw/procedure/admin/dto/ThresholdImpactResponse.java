package com.foreignerwarsaw.procedure.admin.dto;

import java.util.List;

/**
 * Which rules reference this Threshold (brief §48) - names, not counts, since seeing exactly which
 * rule would be affected before withdrawing/republishing a threshold is the point.
 */
public record ThresholdImpactResponse(List<String> referencingRuleCodes) {}
