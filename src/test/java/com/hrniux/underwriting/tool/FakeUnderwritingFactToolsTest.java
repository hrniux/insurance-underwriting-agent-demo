package com.hrniux.underwriting.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.demo.DemoScenarioRepository;
import com.hrniux.underwriting.shared.error.ResourceNotFoundException;

class FakeUnderwritingFactToolsTest {

    private FakeUnderwritingFactTools tools;

    @BeforeEach
    void setUp() {
        tools = new FakeUnderwritingFactTools(DemoScenarioRepository.loadDefault());
    }

    @Test
    void exposesTheHighRiskWarehouseScenario() {
        PolicyFacts policy = tools.getPolicy("P-1001");
        QuotationFacts quotation = tools.getQuotation("P-1001");
        UnderwritingHistoryFacts history = tools.getUnderwritingHistory("P-1001");
        SurveyReportFacts survey = tools.getSurveyReport("P-1001");
        DisasterRiskFacts disaster = tools.getDisasterRisk("P-1001");

        assertThat(policy.occupancy()).isEqualTo("WAREHOUSE");
        assertThat(quotation.sumInsured()).isEqualByComparingTo(new BigDecimal("20000000"));
        assertThat(history.claimCountThreeYears()).isEqualTo(2);
        assertThat(survey.drainageRemediationCompleted()).isFalse();
        assertThat(disaster.rainstorm()).isEqualTo(HazardLevel.RED);
    }

    @Test
    void exposesTheLowRiskOfficeScenario() {
        assertThat(tools.getPolicy("P-2001").occupancy()).isEqualTo("OFFICE");
        assertThat(tools.getUnderwritingHistory("P-2001").claimCountThreeYears()).isZero();
        assertThat(tools.getSurveyReport("P-2001").openIssues()).isEmpty();
        assertThat(tools.getDisasterRisk("P-2001").rainstorm()).isEqualTo(HazardLevel.LOW);
    }

    @Test
    void exposesTheMediumRiskAndRejectedTeachingScenarios() {
        assertThat(tools.getPolicy("P-3001").occupancy()).isEqualTo("WAREHOUSE");
        assertThat(tools.getQuotation("P-3001").sumInsured())
                .isEqualByComparingTo(new BigDecimal("12000000"));
        assertThat(tools.getDisasterRisk("P-3001").rainstorm()).isEqualTo(HazardLevel.RED);
        assertThat(tools.getSurveyReport("P-3001").drainageRemediationCompleted()).isTrue();

        assertThat(tools.getPolicy("P-4001").occupancy()).isEqualTo("FACTORY");
        assertThat(tools.getUnderwritingHistory("P-4001").claimCountThreeYears()).isZero();
        assertThat(tools.getSurveyReport("P-4001").fireProtectionStatus()).isEqualTo("CRITICAL_DEFECT");
        assertThat(tools.getDisasterRisk("P-4001").fire()).isEqualTo(HazardLevel.EXTREME);
    }

    @Test
    void reportsAnUnknownPolicyWithADomainCode() {
        assertThatThrownBy(() -> tools.getPolicy("P-MISSING"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(error -> assertThat(((ResourceNotFoundException) error).errorCode())
                        .isEqualTo("POLICY_NOT_FOUND"));
    }
}
