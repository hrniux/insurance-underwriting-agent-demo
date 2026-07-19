package com.hrniux.underwriting.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.RiskLevel;
import com.hrniux.underwriting.rule.RuleEvaluation;
import com.hrniux.underwriting.rule.RuleResult;
import com.hrniux.underwriting.rule.RuleSeverity;

class DeterministicMockModelGatewayTest {

    @Test
    void producesAStableChineseExplanationFromRulesAndEvidence() {
        DeterministicMockModelGateway gateway = new DeterministicMockModelGateway("demo-underwriter-v1");
        ModelRequest request = new ModelRequest("请给出核保建议", new RuleEvaluation(
                Decision.MANUAL_REVIEW,
                RiskLevel.HIGH,
                70,
                List.of(new RuleResult("RED_RAINSTORM", "暴雨风险为红色", RuleSeverity.HIGH, 20,
                        Decision.MANUAL_REVIEW, 10))),
                List.of("条款要求红色暴雨区域转人工复核"),
                List.of());

        ModelResponse response = gateway.generate(request);

        assertThat(response.summary()).contains("人工复核", "70");
        assertThat(response.reasons()).contains("暴雨风险为红色");
        assertThat(response.recommendedActions()).isNotEmpty();
        assertThat(response.provider()).isEqualTo("mock");
        assertThat(response.model()).isEqualTo("demo-underwriter-v1");
        assertThat(response.attempts()).isOne();
        assertThat(response.fallbackUsed()).isFalse();
    }

    @Test
    void turnsDataQualityWarningsIntoExplicitReasonsAndActions() {
        DeterministicMockModelGateway gateway = new DeterministicMockModelGateway("demo-underwriter-v1");
        ModelRequest request = new ModelRequest(
                "灾害风险未知",
                new RuleEvaluation(Decision.MANUAL_REVIEW, RiskLevel.LOW, 10, List.of()),
                List.of("办公楼条款证据"),
                List.of("灾害风险数据暂时不可用，必须人工补充核验。"));

        ModelResponse response = gateway.generate(request);

        assertThat(response.summary()).contains("数据质量告警", "未知风险不得按低风险处理");
        assertThat(response.reasons()).contains("灾害风险数据暂时不可用，必须人工补充核验。");
        assertThat(response.recommendedActions()).contains("补充并核验缺失的外部灾害风险数据");
    }
}
