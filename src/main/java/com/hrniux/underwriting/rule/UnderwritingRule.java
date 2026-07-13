package com.hrniux.underwriting.rule;

import java.util.Optional;

@FunctionalInterface
public interface UnderwritingRule {

    Optional<RuleResult> evaluate(UnderwritingContext context);
}
