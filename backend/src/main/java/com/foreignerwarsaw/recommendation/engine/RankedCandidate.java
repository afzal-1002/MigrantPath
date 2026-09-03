package com.foreignerwarsaw.recommendation.engine;

import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.recommendation.core.RecommendationType;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationResult;
import java.util.List;

/**
 * One candidate after {@link RecommendationRanker} has assigned its final, deterministic position
 * (brief §22/§44) - {@link #type} may differ from the classifier's own output (a PRIMARY_MATCH
 * candidate demoted to POSSIBLE_ALTERNATIVE).
 */
public record RankedCandidate(
    Procedure procedure,
    ProcedureVersion procedureVersion,
    RecommendationType type,
    List<RuleEvaluationResult> ruleResults,
    int rank) {}
