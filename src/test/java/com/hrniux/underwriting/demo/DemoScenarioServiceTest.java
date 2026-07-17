package com.hrniux.underwriting.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DemoScenarioServiceTest {

    private DemoScenarioService service;

    @BeforeEach
    void setUp() {
        service = new DemoScenarioService(DemoScenarioRepository.loadDefault());
    }

    @Test
    void listsSortedChineseFriendlyScenarioSummaries() {
        assertThat(service.list()).extracting(DemoScenarioViews.Summary::policyNo)
                .containsExactly("P-1001", "P-2001", "P-3001", "P-4001");
        assertThat(service.list().getFirst().expectedResult().decisionLabel()).isEqualTo("人工复核");
        assertThat(service.list().getFirst().expectedResult().riskLevelLabel()).isEqualTo("高风险");
        assertThat(service.list().getLast().expectedResult().decisionLabel()).isEqualTo("拒保");
    }

    @Test
    void formatsMoneyAndKeepsRawFactsInTheDetail() {
        DemoScenarioViews.Detail detail = service.get("P-1001");

        assertThat(detail.sumInsuredDisplay()).isEqualTo("2,000 万元");
        assertThat(detail.premiumDisplay()).isEqualTo("7 万元");
        assertThat(detail.deductibleDisplay()).isEqualTo("20 万元");
        assertThat(detail.paidLossThreeYearsDisplay()).isEqualTo("120 万元");
        assertThat(detail.quotation().sumInsured()).isEqualByComparingTo("20000000");
        assertThat(detail.history().paidLossThreeYears()).isEqualByComparingTo("1200000");
    }

    @Test
    void providesLabelsForEveryDecisionAndRiskLevelUsedByTheScenarios() {
        assertThat(service.get("P-2001").expectedResult().decisionLabel()).isEqualTo("自动通过");
        assertThat(service.get("P-2001").expectedResult().riskLevelLabel()).isEqualTo("低风险");
        assertThat(service.get("P-3001").expectedResult().riskLevelLabel()).isEqualTo("中风险");
        assertThat(service.get("P-4001").expectedResult().decisionLabel()).isEqualTo("拒保");
    }
}
