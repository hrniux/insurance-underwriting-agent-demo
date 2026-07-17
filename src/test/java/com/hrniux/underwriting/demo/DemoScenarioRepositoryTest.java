package com.hrniux.underwriting.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.demo.DemoScenario.ExpectedResult;
import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.RiskLevel;
import com.hrniux.underwriting.tool.DisasterRiskFacts;
import com.hrniux.underwriting.tool.HazardLevel;
import com.hrniux.underwriting.tool.PolicyFacts;
import com.hrniux.underwriting.tool.QuotationFacts;
import com.hrniux.underwriting.tool.SurveyReportFacts;
import com.hrniux.underwriting.tool.UnderwritingHistoryFacts;

class DemoScenarioRepositoryTest {

    @Test
    void loadsFourSortedScenariosFromTheDefaultResource() {
        DemoScenarioRepository repository = DemoScenarioRepository.loadDefault();

        assertThat(repository.findAll()).extracting(DemoScenario::policyNo)
                .containsExactly("P-1001", "P-2001", "P-3001", "P-4001");
        assertThat(repository.required("P-3001").name()).isEqualTo("暴雨暴露商贸仓库");
        assertThat(repository.required("P-4001").expectedResult().decision()).isEqualTo(Decision.REJECT);
    }

    @Test
    void rejectsAnEmptyScenarioList() {
        assertThatThrownBy(() -> new DemoScenarioRepository(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void rejectsDuplicatePolicyNumbers() {
        DemoScenario scenario = validScenario("P-TEST-1");

        assertThatThrownBy(() -> new DemoScenarioRepository(List.of(scenario, scenario)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复保单号")
                .hasMessageContaining("P-TEST-1");
    }

    @Test
    void rejectsAMismatchedChildPolicyNumber() {
        DemoScenario source = validScenario("P-TEST-1");
        DemoScenario mismatch = new DemoScenario(
                source.policyNo(), source.name(), source.summary(), source.question(), source.learningPoints(),
                source.expectedResult(),
                new PolicyFacts("P-OTHER", "PROPERTY", "虚构企业", "OFFICE", "虚构地址",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)),
                source.quotation(), source.history(), source.survey(), source.disaster());

        assertThatThrownBy(() -> new DemoScenarioRepository(List.of(mismatch)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("保单号不一致")
                .hasMessageContaining("P-TEST-1");
    }

    @Test
    void rejectsNegativeMoney() {
        DemoScenario source = validScenario("P-TEST-1");
        DemoScenario negative = new DemoScenario(
                source.policyNo(), source.name(), source.summary(), source.question(), source.learningPoints(),
                source.expectedResult(), source.policy(),
                new QuotationFacts(source.policyNo(), new BigDecimal("-1"), new BigDecimal("0.001"),
                        new BigDecimal("100"), BigDecimal.ZERO),
                source.history(), source.survey(), source.disaster());

        assertThatThrownBy(() -> new DemoScenarioRepository(List.of(negative)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能为负数")
                .hasMessageContaining("P-TEST-1");
    }

    @Test
    void rejectsAnInsuranceEndDateThatIsNotAfterTheStartDate() {
        DemoScenario source = validScenario("P-TEST-1");
        PolicyFacts invalidDates = new PolicyFacts(source.policyNo(), "PROPERTY", "虚构企业", "OFFICE", "虚构地址",
                LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1));
        DemoScenario invalid = new DemoScenario(
                source.policyNo(), source.name(), source.summary(), source.question(), source.learningPoints(),
                source.expectedResult(),
                invalidDates, source.quotation(), source.history(), source.survey(), source.disaster());

        assertThatThrownBy(() -> new DemoScenarioRepository(List.of(invalid)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("保险终期");
    }

    @Test
    void rejectsARiskScoreOutsideTheSupportedRange() {
        DemoScenario source = validScenario("P-TEST-1");
        DemoScenario invalid = new DemoScenario(
                source.policyNo(), source.name(), source.summary(), source.question(), source.learningPoints(),
                new ExpectedResult(Decision.APPROVE, RiskLevel.LOW, 101, List.of()),
                source.policy(), source.quotation(), source.history(), source.survey(), source.disaster());

        assertThatThrownBy(() -> new DemoScenarioRepository(List.of(invalid)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("风险分");
    }

    @Test
    void rejectsDuplicateExpectedRuleCodes() {
        DemoScenario source = validScenario("P-TEST-1");
        DemoScenario invalid = new DemoScenario(
                source.policyNo(), source.name(), source.summary(), source.question(), source.learningPoints(),
                new ExpectedResult(Decision.APPROVE, RiskLevel.LOW, 10, List.of("RULE-A", "RULE-A")),
                source.policy(), source.quotation(), source.history(), source.survey(), source.disaster());

        assertThatThrownBy(() -> new DemoScenarioRepository(List.of(invalid)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("规则代码不能重复");
    }

    private DemoScenario validScenario(String policyNo) {
        return new DemoScenario(
                policyNo,
                "校验测试场景",
                "只用于测试场景数据校验。",
                "这张保单是否可以承保？",
                List.of("理解测试数据"),
                new ExpectedResult(Decision.APPROVE, RiskLevel.LOW, 10, List.of()),
                new PolicyFacts(policyNo, "PROPERTY", "虚构企业", "OFFICE", "虚构地址",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)),
                new QuotationFacts(policyNo, new BigDecimal("1000000"), new BigDecimal("0.001"),
                        new BigDecimal("1000"), new BigDecimal("10000")),
                new UnderwritingHistoryFacts(policyNo, 0, BigDecimal.ZERO, List.of("首次投保")),
                new SurveyReportFacts(policyNo, "GOOD", true, List.of(), "无待整改事项"),
                new DisasterRiskFacts(policyNo, "虚构区域", HazardLevel.LOW, HazardLevel.LOW,
                        HazardLevel.LOW, LocalDate.of(2026, 7, 1)));
    }
}
