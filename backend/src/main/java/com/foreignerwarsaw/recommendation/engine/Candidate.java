package com.foreignerwarsaw.recommendation.engine;

import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedureVersion;
import com.foreignerwarsaw.recommendation.core.RecommendationType;
import com.foreignerwarsaw.rules.evaluation.RuleEvaluationResult;
import java.util.List;

/** One classified-but-not-yet-ranked candidate procedure (brief §13's step 6-7 boundary). */
public record Candidate(
    Procedure procedure,
    ProcedureVersion procedureVersion,
    RecommendationType type,
    List<RuleEvaluationResult> ruleResults) {}
