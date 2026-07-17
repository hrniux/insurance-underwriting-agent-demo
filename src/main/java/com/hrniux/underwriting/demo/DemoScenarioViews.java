package com.hrniux.underwriting.demo;

import java.util.List;

import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.RiskLevel;
import com.hrniux.underwriting.tool.DisasterRiskFacts;
import com.hrniux.underwriting.tool.PolicyFacts;
import com.hrniux.underwriting.tool.QuotationFacts;
import com.hrniux.underwriting.tool.SurveyReportFacts;
import com.hrniux.underwriting.tool.UnderwritingHistoryFacts;

public final class DemoScenarioViews {

    private DemoScenarioViews() {
    }

    public record ExpectedResultView(
            Decision decision,
            String decisionLabel,
            RiskLevel riskLevel,
            String riskLevelLabel,
            int riskScore,
            List<String> ruleCodes) {

        public ExpectedResultView {
            ruleCodes = List.copyOf(ruleCodes);
        }
    }

    public record Summary(
            String policyNo,
            String name,
            String summary,
            String question,
            List<String> learningPoints,
            ExpectedResultView expectedResult) {

        public Summary {
            learningPoints = List.copyOf(learningPoints);
        }
    }

    public record Detail(
            String policyNo,
            String name,
            String summary,
            String question,
            List<String> learningPoints,
            ExpectedResultView expectedResult,
            String sumInsuredDisplay,
            String premiumDisplay,
            String deductibleDisplay,
            String paidLossThreeYearsDisplay,
            PolicyFacts policy,
            QuotationFacts quotation,
            UnderwritingHistoryFacts history,
            SurveyReportFacts survey,
            DisasterRiskFacts disaster) {

        public Detail {
            learningPoints = List.copyOf(learningPoints);
        }
    }
}
