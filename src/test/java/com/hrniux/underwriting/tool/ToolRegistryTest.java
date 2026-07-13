package com.hrniux.underwriting.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.shared.error.ResourceNotFoundException;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry(
                new FakeUnderwritingFactTools(),
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
    void listsAllAgentToolNamesIncludingRuleValidation() {
        assertThat(registry.toolNames()).containsExactly(ToolName.values());
    }
}
