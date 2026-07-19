package com.hrniux.underwriting.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.demo.DemoScenarioRepository;
import com.hrniux.underwriting.shared.error.ResourceNotFoundException;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry(
                new FakeUnderwritingFactTools(DemoScenarioRepository.loadDefault()),
                Clock.fixed(Instant.parse("2026-07-13T06:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void invokesATypedToolAndRecordsASanitizedTrace() {
        ToolInvocation<?> invocation = registry.invoke(ToolName.GET_POLICY, "P-1001");

        assertThat(invocation.result()).isInstanceOf(PolicyFacts.class);
        assertThat(invocation.trace().toolName()).isEqualTo(ToolName.GET_POLICY);
        assertThat(invocation.trace().status()).isEqualTo(ToolCallStatus.SUCCESS);
        assertThat(invocation.trace().inputSummary()).isEqualTo("policyNo=P-1001");
        assertThat(invocation.trace().outputSummary()).contains("P-1001").doesNotContain("Authorization");
        assertThat(invocation.trace().durationMs()).isNotNegative();
    }

    @Test
    void KeepsAFailedTraceBeforeRethrowingTheDomainError() {
        assertThatThrownBy(() -> registry.invoke(ToolName.GET_POLICY, "P-MISSING"))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(registry.traces()).singleElement().satisfies(trace -> {
            assertThat(trace.status()).isEqualTo(ToolCallStatus.FAILED);
            assertThat(trace.errorCode()).isEqualTo("POLICY_NOT_FOUND");
            assertThat(trace.outputSummary()).isEqualTo("tool call failed");
        });
    }

    @Test
    void returnsAFailedAttemptWithTheSameAuditTraceForDegradableCallers() {
        ToolAttempt<?> attempt = registry.tryInvoke(ToolName.GET_DISASTER_RISK, "P-MISSING");

        assertThat(attempt.failed()).isTrue();
        assertThat(attempt.failure()).isInstanceOf(ResourceNotFoundException.class);
        assertThat(attempt.trace().status()).isEqualTo(ToolCallStatus.FAILED);
        assertThat(attempt.trace().errorCode()).isEqualTo("POLICY_NOT_FOUND");
    }

    @Test
    void treatsANullToolResultAsOneFailedAttempt() {
        UnderwritingFactTools nullReturningTools = mock(UnderwritingFactTools.class);
        when(nullReturningTools.getPolicy("P-NULL")).thenReturn(null);
        ToolRegistry nullRegistry = new ToolRegistry(
                nullReturningTools,
                Clock.fixed(Instant.parse("2026-07-13T06:00:00Z"), ZoneOffset.UTC));

        ToolAttempt<?> attempt = nullRegistry.tryInvoke(ToolName.GET_POLICY, "P-NULL");

        assertThat(attempt.failed()).isTrue();
        assertThat(attempt.failure()).isInstanceOf(NullPointerException.class);
        assertThat(nullRegistry.traces()).singleElement().satisfies(trace -> {
            assertThat(trace.status()).isEqualTo(ToolCallStatus.FAILED);
            assertThat(trace.errorCode()).isEqualTo("TOOL_CALL_FAILED");
        });
    }

    @Test
    void keepsOnlyTheConfiguredNumberOfRecentGlobalTraces() {
        ToolRegistry bounded = new ToolRegistry(
                new FakeUnderwritingFactTools(DemoScenarioRepository.loadDefault()),
                Clock.fixed(Instant.parse("2026-07-13T06:00:00Z"), ZoneOffset.UTC),
                2);

        bounded.invoke(ToolName.GET_POLICY, "P-1001");
        bounded.invoke(ToolName.GET_QUOTATION, "P-1001");
        bounded.invoke(ToolName.GET_SURVEY_REPORT, "P-1001");

        assertThat(bounded.traces()).extracting(ToolCallTrace::toolName)
                .containsExactly(ToolName.GET_QUOTATION, ToolName.GET_SURVEY_REPORT);
    }

    @Test
    void listsAllAgentToolNamesIncludingRuleValidation() {
        assertThat(registry.toolNames()).containsExactly(ToolName.values());
        assertThat(ToolName.GET_DISASTER_RISK.criticality()).isEqualTo(ToolCriticality.DEGRADABLE);
        assertThat(ToolName.GET_POLICY.criticality()).isEqualTo(ToolCriticality.CRITICAL);
    }
}
