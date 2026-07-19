package com.hrniux.underwriting.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.RiskLevel;
import com.hrniux.underwriting.rule.RuleEvaluation;

class RoutingModelGatewayTest {

    private final ModelRequest request = new ModelRequest("prompt",
            new RuleEvaluation(Decision.APPROVE, RiskLevel.LOW, 10, List.of()), List.of(), List.of());

    @Test
    void explicitlyFallsBackToTheMockProvider() {
        ModelGateway unavailable = ignored -> {
            throw new ModelUnavailableException("MODEL_UNAVAILABLE", "private model unavailable", 2, null);
        };
        RoutingModelGateway router = new RoutingModelGateway(
                unavailable,
                new DeterministicMockModelGateway("fallback-model"),
                true);

        ModelResponse response = router.generate(request);

        assertThat(response.provider()).isEqualTo("mock");
        assertThat(response.fallbackUsed()).isTrue();
    }

    @Test
    void propagatesTheProviderFailureWhenFallbackIsDisabled() {
        ModelGateway unavailable = ignored -> {
            throw new ModelUnavailableException("MODEL_UNAVAILABLE", "private model unavailable", 2, null);
        };
        RoutingModelGateway router = new RoutingModelGateway(
                unavailable,
                new DeterministicMockModelGateway("fallback-model"),
                false);

        assertThatThrownBy(() -> router.generate(request)).isInstanceOf(ModelUnavailableException.class);
    }
}
