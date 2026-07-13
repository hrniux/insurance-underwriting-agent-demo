package com.hrniux.underwriting.tool;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record UnderwritingHistoryFacts(
        String policyNo,
        int claimCountThreeYears,
        BigDecimal paidLossThreeYears,
        List<String> priorDecisions) {

    public UnderwritingHistoryFacts {
        Objects.requireNonNull(policyNo, "policyNo must not be null");
        Objects.requireNonNull(paidLossThreeYears, "paidLossThreeYears must not be null");
        priorDecisions = List.copyOf(priorDecisions);
    }
}
