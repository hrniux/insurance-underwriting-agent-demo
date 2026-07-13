package com.hrniux.underwriting.tool;

public interface UnderwritingFactTools {

    PolicyFacts getPolicy(String policyNo);

    QuotationFacts getQuotation(String policyNo);

    UnderwritingHistoryFacts getUnderwritingHistory(String policyNo);

    SurveyReportFacts getSurveyReport(String policyNo);

    DisasterRiskFacts getDisasterRisk(String policyNo);
}
