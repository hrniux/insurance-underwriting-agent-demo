package com.hrniux.underwriting.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.agent.EvaluationRequest;

class UnderwritingTaskTest {

    private static final EvaluationRequest REQUEST = new EvaluationRequest(null, "P-1001", "是否承保？");
    private static final Instant CREATED = Instant.parse("2026-07-19T10:00:00Z");

    @Test
    void followsTheOnlyValidSuccessStateMachine() {
        UnderwritingTask pending = UnderwritingTask.pending("TASK-1", REQUEST, CREATED);
        UnderwritingTask running = pending.start(CREATED.plusSeconds(1));
        UnderwritingTask succeeded = running.succeed("EVAL-1", CREATED.plusSeconds(2));

        assertThat(pending.status()).isEqualTo(UnderwritingTaskStatus.PENDING);
        assertThat(running.status()).isEqualTo(UnderwritingTaskStatus.RUNNING);
        assertThat(succeeded.status()).isEqualTo(UnderwritingTaskStatus.SUCCEEDED);
        assertThat(succeeded.evaluationId()).isEqualTo("EVAL-1");
        assertThat(succeeded.failure()).isNull();
        assertThat(succeeded.terminal()).isTrue();
    }

    @Test
    void recordsFailureWithoutAnEvaluationAndRejectsIllegalTransitions() {
        UnderwritingTask running = UnderwritingTask.pending("TASK-2", REQUEST, CREATED)
                .start(CREATED.plusSeconds(1));
        UnderwritingTask failed = running.fail(
                new UnderwritingTaskFailure("MODEL_TIMEOUT", "Model request timed out"),
                CREATED.plusSeconds(2));

        assertThat(failed.status()).isEqualTo(UnderwritingTaskStatus.FAILED);
        assertThat(failed.evaluationId()).isNull();
        assertThat(failed.failure().errorCode()).isEqualTo("MODEL_TIMEOUT");
        assertThatThrownBy(() -> failed.succeed("EVAL-LATE", CREATED.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot transition");
    }

    @Test
    void rejectsNonMonotonicLifecycleTimestamps() {
        assertThatThrownBy(() -> new UnderwritingTask(
                "TASK-3",
                REQUEST,
                UnderwritingTaskStatus.RUNNING,
                null,
                null,
                CREATED,
                CREATED.minusSeconds(1),
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startedAt");
    }
}
