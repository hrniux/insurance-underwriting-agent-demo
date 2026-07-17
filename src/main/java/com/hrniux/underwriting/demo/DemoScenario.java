package com.hrniux.underwriting.demo;

import java.util.List;
import java.util.Objects;

import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.RiskLevel;
import com.hrniux.underwriting.tool.DisasterRiskFacts;
import com.hrniux.underwriting.tool.PolicyFacts;
import com.hrniux.underwriting.tool.QuotationFacts;
import com.hrniux.underwriting.tool.SurveyReportFacts;
import com.hrniux.underwriting.tool.UnderwritingHistoryFacts;

public record DemoScenario(
        String policyNo,
        String name,
        String summary,
        String question,
        List<String> learningPoints,
        ExpectedResult expectedResult,
        PolicyFacts policy,
        QuotationFacts quotation,
        UnderwritingHistoryFacts history,
        SurveyReportFacts survey,
        DisasterRiskFacts disaster) {

    public DemoScenario {
        learningPoints = List.copyOf(Objects.requireNonNull(learningPoints, "learningPoints must not be null"));
    }

    public record ExpectedResult(
            Decision decision,
            RiskLevel riskLevel,
            int riskScore,
            List<String> ruleCodes) {

        public ExpectedResult {
            ruleCodes = List.copyOf(Objects.requireNonNull(ruleCodes, "ruleCodes must not be null"));
        }
    }
}
