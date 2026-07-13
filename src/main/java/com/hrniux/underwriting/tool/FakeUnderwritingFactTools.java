package com.hrniux.underwriting.tool;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.hrniux.underwriting.shared.error.ResourceNotFoundException;

@Service
public class FakeUnderwritingFactTools implements UnderwritingFactTools {

    private static final String HIGH_RISK_POLICY = "P-1001";
    private static final String LOW_RISK_POLICY = "P-2001";

    private final Map<String, PolicyFacts> policies = Map.of(
            HIGH_RISK_POLICY, new PolicyFacts(HIGH_RISK_POLICY, "PROPERTY", "华东示例物流有限公司",
                    "WAREHOUSE", "海州市临港区示例路 100 号", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)),
            LOW_RISK_POLICY, new PolicyFacts(LOW_RISK_POLICY, "PROPERTY", "海州示例科技有限公司",
                    "OFFICE", "海州市高新区示例大道 20 号", LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 1)));

    private final Map<String, QuotationFacts> quotations = Map.of(
            HIGH_RISK_POLICY, new QuotationFacts(HIGH_RISK_POLICY, new BigDecimal("20000000"),
                    new BigDecimal("0.0035"), new BigDecimal("70000"), new BigDecimal("200000")),
            LOW_RISK_POLICY, new QuotationFacts(LOW_RISK_POLICY, new BigDecimal("5000000"),
                    new BigDecimal("0.0012"), new BigDecimal("6000"), new BigDecimal("50000")));

    private final Map<String, UnderwritingHistoryFacts> histories = Map.of(
            HIGH_RISK_POLICY, new UnderwritingHistoryFacts(HIGH_RISK_POLICY, 2, new BigDecimal("1200000"),
                    List.of("2024 年续保转人工复核", "2025 年附加防洪免赔条件承保")),
            LOW_RISK_POLICY, new UnderwritingHistoryFacts(LOW_RISK_POLICY, 0, BigDecimal.ZERO,
                    List.of("2025 年自动核保通过")));

    private final Map<String, SurveyReportFacts> surveys = Map.of(
            HIGH_RISK_POLICY, new SurveyReportFacts(HIGH_RISK_POLICY, "ADEQUATE", false,
                    List.of("排水沟扩容未完成", "防水挡板证明待补充"), "建议完成防洪整改后人工复核"),
            LOW_RISK_POLICY, new SurveyReportFacts(LOW_RISK_POLICY, "GOOD", true, List.of(),
                    "办公场所管理规范，无待整改事项"));

    private final Map<String, DisasterRiskFacts> disasters = Map.of(
            HIGH_RISK_POLICY, new DisasterRiskFacts(HIGH_RISK_POLICY, "临港示例区", HazardLevel.RED,
                    HazardLevel.HIGH, HazardLevel.MEDIUM, LocalDate.of(2026, 7, 1)),
            LOW_RISK_POLICY, new DisasterRiskFacts(LOW_RISK_POLICY, "高新示例区", HazardLevel.LOW,
                    HazardLevel.LOW, HazardLevel.LOW, LocalDate.of(2026, 7, 1)));

    @Override
    public PolicyFacts getPolicy(String policyNo) {
        return required(policies, policyNo);
    }

    @Override
    public QuotationFacts getQuotation(String policyNo) {
        return required(quotations, policyNo);
    }

    @Override
    public UnderwritingHistoryFacts getUnderwritingHistory(String policyNo) {
        return required(histories, policyNo);
    }

    @Override
    public SurveyReportFacts getSurveyReport(String policyNo) {
        return required(surveys, policyNo);
    }

    @Override
    public DisasterRiskFacts getDisasterRisk(String policyNo) {
        return required(disasters, policyNo);
    }

    private <T> T required(Map<String, T> values, String policyNo) {
        T value = values.get(policyNo);
        if (value == null) {
            throw new ResourceNotFoundException("POLICY_NOT_FOUND", policyNo);
        }
        return value;
    }
}
