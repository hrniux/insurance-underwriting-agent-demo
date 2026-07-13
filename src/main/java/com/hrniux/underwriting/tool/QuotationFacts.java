package com.hrniux.underwriting.tool;

import java.math.BigDecimal;
import java.util.Objects;

public record QuotationFacts(
        String policyNo,
        BigDecimal sumInsured,
        BigDecimal rate,
        BigDecimal premium,
        BigDecimal deductible) {

    public QuotationFacts {
        Objects.requireNonNull(policyNo, "policyNo must not be null");
        Objects.requireNonNull(sumInsured, "sumInsured must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(premium, "premium must not be null");
        Objects.requireNonNull(deductible, "deductible must not be null");
    }
}
