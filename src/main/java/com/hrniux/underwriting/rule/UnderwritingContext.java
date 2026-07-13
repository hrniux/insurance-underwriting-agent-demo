package com.hrniux.underwriting.rule;

import java.util.Objects;

import com.hrniux.underwriting.tool.DisasterRiskFacts;
import com.hrniux.underwriting.tool.PolicyFacts;
import com.hrniux.underwriting.tool.QuotationFacts;
import com.hrniux.underwriting.tool.SurveyReportFacts;
import com.hrniux.underwriting.tool.UnderwritingHistoryFacts;

public record UnderwritingContext(
        PolicyFacts policy,
        QuotationFacts quotation,
        UnderwritingHistoryFacts history,
        SurveyReportFacts survey,
        DisasterRiskFacts disaster) {

    public UnderwritingContext {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(quotation, "quotation must not be null");
        Objects.requireNonNull(history, "history must not be null");
        Objects.requireNonNull(survey, "survey must not be null");
        Objects.requireNonNull(disaster, "disaster must not be null");
    }
}
