package com.hrniux.underwriting.rule;

import java.util.Objects;

public record RuleResult(
        String code,
        String reason,
        RuleSeverity severity,
        int scoreImpact,
        Decision decision,
        int priority) {

    public RuleResult {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
    }
}
