package com.hrniux.underwriting.rule;

import java.util.List;
import java.util.Objects;

public record RuleEvaluation(
        Decision decision,
        RiskLevel riskLevel,
        int riskScore,
        List<RuleResult> hits) {

    public RuleEvaluation {
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        hits = List.copyOf(hits);
    }
}
