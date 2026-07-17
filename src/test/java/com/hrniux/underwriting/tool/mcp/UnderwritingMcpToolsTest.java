package com.hrniux.underwriting.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.demo.DemoScenarioRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.UnderwritingRuleEngine;
import com.hrniux.underwriting.tool.FakeUnderwritingFactTools;
import com.hrniux.underwriting.tool.HazardLevel;
import com.hrniux.underwriting.tool.ToolRegistry;

class UnderwritingMcpToolsTest {

    private static final Set<String> EXPECTED_NAMES = Set.of(
            "get_policy",
            "get_quotation",
            "get_underwriting_history",
            "get_survey_report",
            "get_disaster_risk",
            "validate_rules");

    private UnderwritingMcpTools tools;

    @BeforeEach
    void setUp() {
        tools = new UnderwritingMcpTools(
                new ToolRegistry(new FakeUnderwritingFactTools(DemoScenarioRepository.loadDefault())),
                new UnderwritingRuleEngine());
    }

    @Test
    void declaresExactlySixStronglyTypedRequiredMcpTools() {
        Method[] methods = Arrays.stream(UnderwritingMcpTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .toArray(Method[]::new);

        assertThat(Arrays.stream(methods)
                .map(method -> method.getAnnotation(McpTool.class).name())
                .collect(Collectors.toSet())).isEqualTo(EXPECTED_NAMES);
        assertThat(methods).hasSize(6).allSatisfy(method -> {
            assertThat(method.getAnnotation(McpTool.class).generateOutputSchema()).isTrue();
            assertThat(method.getParameters()).singleElement().satisfies(parameter -> {
                McpToolParam annotation = parameter.getAnnotation(McpToolParam.class);
                assertThat(annotation).isNotNull();
                assertThat(annotation.required()).isTrue();
            });
        });
    }

    @Test
    void delegatesAllFactsAndRuleValidationToTheSharedBusinessLayer() {
        assertThat(tools.getPolicy("P-1001").policyNo()).isEqualTo("P-1001");
        assertThat(tools.getQuotation("P-1001").sumInsured()).isPositive();
        assertThat(tools.getUnderwritingHistory("P-1001").claimCountThreeYears()).isEqualTo(2);
        assertThat(tools.getSurveyReport("P-1001").openIssues()).isNotEmpty();
        assertThat(tools.getDisasterRisk("P-1001").rainstorm()).isEqualTo(HazardLevel.RED);
        assertThat(tools.validateRules("P-1001").decision()).isEqualTo(Decision.MANUAL_REVIEW);
    }
}
