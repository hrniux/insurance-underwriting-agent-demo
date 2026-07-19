package com.hrniux.underwriting.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.model.ModelResponse;
import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.RiskLevel;
import com.hrniux.underwriting.shared.config.IdempotencyProperties;
import com.hrniux.underwriting.shared.error.ConflictException;
import com.hrniux.underwriting.shared.error.InvalidRequestException;
import com.hrniux.underwriting.tool.ToolName;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class EvaluationSubmissionServiceTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private UnderwritingAgentOrchestrator orchestrator;
    private SimpleMeterRegistry metrics;
    private EvaluationSubmissionService service;

    @BeforeEach
    void setUp() {
        orchestrator = mock(UnderwritingAgentOrchestrator.class);
        metrics = new SimpleMeterRegistry();
        service = new EvaluationSubmissionService(
                orchestrator,
                new IdempotencyProperties(100, Duration.ofHours(1)),
                metrics,
                Clock.fixed(Instant.parse("2026-07-19T06:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        metrics.close();
    }

    @Test
    void replaysACompletedEvaluationWithoutRunningTheAgentAgain() {
        EvaluationRequest request = request("是否承保？");
        when(orchestrator.evaluate(request)).thenReturn(evaluation("EVAL-1", request));

        EvaluationSubmissionResult first = service.submit(request, "interview-request-001");
        EvaluationSubmissionResult replay = service.submit(request, "interview-request-001");

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.evaluation()).isSameAs(first.evaluation());
        verify(orchestrator).evaluate(request);
        assertThat(counter("created")).isEqualTo(1.0);
        assertThat(counter("replayed")).isEqualTo(1.0);
    }

    @Test
    void rejectsReusingAKeyForADifferentBusinessRequest() {
        EvaluationRequest first = request("是否承保？");
        EvaluationRequest changed = request("是否需要人工复核？");
        when(orchestrator.evaluate(first)).thenReturn(evaluation("EVAL-1", first));

        service.submit(first, "interview-request-002");

        assertThatThrownBy(() -> service.submit(changed, "interview-request-002"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("different request");
        verify(orchestrator).evaluate(first);
        assertThat(counter("conflict")).isEqualTo(1.0);
    }

    @Test
    void removesAFailedSlotSoTheSameKeyCanBeRetried() {
        EvaluationRequest request = request("是否承保？");
        when(orchestrator.evaluate(request))
                .thenThrow(new IllegalStateException("temporary failure"))
                .thenReturn(evaluation("EVAL-RETRY", request));

        assertThatThrownBy(() -> service.submit(request, "interview-request-003"))
                .isInstanceOf(IllegalStateException.class);

        EvaluationSubmissionResult retried = service.submit(request, "interview-request-003");

        assertThat(retried.replayed()).isFalse();
        assertThat(retried.evaluation().id()).isEqualTo("EVAL-RETRY");
        verify(orchestrator, times(2)).evaluate(request);
        assertThat(counter("failed")).isEqualTo(1.0);
        assertThat(counter("created")).isEqualTo(1.0);
    }

    @Test
    void collapsesConcurrentDuplicateSubmissionsIntoOneAgentExecution() throws Exception {
        EvaluationRequest request = request("并发请求是否承保？");
        CountDownLatch agentStarted = new CountDownLatch(1);
        CountDownLatch releaseAgent = new CountDownLatch(1);
        when(orchestrator.evaluate(any())).thenAnswer(invocation -> {
            agentStarted.countDown();
            releaseAgent.await();
            return evaluation("EVAL-CONCURRENT", invocation.getArgument(0));
        });

        Future<EvaluationSubmissionResult> first = executor.submit(
                () -> service.submit(request, "interview-request-004"));
        assertThat(agentStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Future<EvaluationSubmissionResult> second = executor.submit(
                () -> service.submit(request, "interview-request-004"));
        releaseAgent.countDown();

        List<EvaluationSubmissionResult> results = List.of(first.get(), second.get());
        assertThat(results).extracting(result -> result.evaluation().id())
                .containsOnly("EVAL-CONCURRENT");
        assertThat(results).extracting(EvaluationSubmissionResult::replayed)
                .containsExactlyInAnyOrder(false, true);
        verify(orchestrator).evaluate(request);
    }

    @Test
    void rejectsUnsafeOrOversizedIdempotencyKeysBeforeRunningTheAgent() {
        EvaluationRequest request = request("是否承保？");

        assertThatThrownBy(() -> service.submit(request, "contains spaces"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.submit(request, "x".repeat(129)))
                .isInstanceOf(InvalidRequestException.class);
        verify(orchestrator, times(0)).evaluate(any());
        assertThat(counter("failed")).isEqualTo(2.0);
    }

    @Test
    void recordsADegradationMetricOnlyForTheActualAgentExecution() {
        EvaluationRequest request = request("灾害数据缺失时是否承保？");
        when(orchestrator.evaluate(request)).thenReturn(degradedEvaluation("EVAL-DEGRADED", request));

        service.submit(request, "interview-request-degraded");
        service.submit(request, "interview-request-degraded");

        assertThat(metrics.get("underwriting.agent.degradations")
                .tag("tool", "GET_DISASTER_RISK")
                .tag("reason", "NON_CRITICAL_TOOL_UNAVAILABLE")
                .counter()
                .count()).isEqualTo(1.0);
        verify(orchestrator).evaluate(request);
    }

    private double counter(String outcome) {
        return metrics.get("underwriting.evaluation.submissions")
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private EvaluationRequest request(String question) {
        return new EvaluationRequest(null, "P-2001", question);
    }

    private UnderwritingEvaluation evaluation(String id, EvaluationRequest request) {
        ModelResponse model = new ModelResponse(
                "建议自动承保", List.of("风险较低"), List.of("保存评估记录"),
                "mock", "test-model", 1, false);
        return new UnderwritingEvaluation(
                id, "SES-1", request.policyNo(), request.question(), Decision.APPROVE, RiskLevel.LOW, 10,
                model.summary(), model.reasons(), model.recommendedActions(), List.of(), List.of(), List.of(),
                List.of(), List.of(), model, Instant.parse("2026-07-19T06:00:00Z"));
    }

    private UnderwritingEvaluation degradedEvaluation(String id, EvaluationRequest request) {
        ModelResponse model = new ModelResponse(
                "建议转人工复核", List.of(), List.of("补充灾害风险数据"),
                "mock", "test-model", 1, false);
        DegradationNotice degradation = new DegradationNotice(
                "NON_CRITICAL_TOOL_UNAVAILABLE",
                ToolName.GET_DISASTER_RISK,
                "TOOL_CALL_FAILED",
                "灾害风险数据暂时不可用。",
                Decision.MANUAL_REVIEW);
        return new UnderwritingEvaluation(
                id, "SES-1", request.policyNo(), request.question(), Decision.MANUAL_REVIEW, RiskLevel.LOW, 10,
                model.summary(), model.reasons(), model.recommendedActions(), List.of(degradation), List.of(),
                List.of(), List.of(), List.of(), model, Instant.parse("2026-07-19T06:00:00Z"));
    }
}
