package com.hrniux.underwriting.tool;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.hrniux.underwriting.demo.DemoScenarioRepository;

@Service
public class FakeUnderwritingFactTools implements UnderwritingFactTools {

    private final DemoScenarioRepository scenarios;

    public FakeUnderwritingFactTools(DemoScenarioRepository scenarios) {
        this.scenarios = Objects.requireNonNull(scenarios);
    }

    @Override
    public PolicyFacts getPolicy(String policyNo) {
        return scenarios.required(policyNo).policy();
    }

    @Override
    public QuotationFacts getQuotation(String policyNo) {
        return scenarios.required(policyNo).quotation();
    }

    @Override
    public UnderwritingHistoryFacts getUnderwritingHistory(String policyNo) {
        return scenarios.required(policyNo).history();
    }

    @Override
    public SurveyReportFacts getSurveyReport(String policyNo) {
        return scenarios.required(policyNo).survey();
    }

    @Override
    public DisasterRiskFacts getDisasterRisk(String policyNo) {
        return scenarios.required(policyNo).disaster();
    }
}
