package com.hrniux.underwriting.task;

import java.time.Instant;
import java.util.Objects;

import com.hrniux.underwriting.agent.EvaluationRequest;

public record UnderwritingTask(
        String id,
        EvaluationRequest request,
        UnderwritingTaskStatus status,
        String evaluationId,
        UnderwritingTaskFailure failure,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {

    public UnderwritingTask {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        requireState(startedAt == null || !startedAt.isBefore(createdAt),
                "startedAt must not be before createdAt");
        requireState(completedAt == null || (startedAt != null && !completedAt.isBefore(startedAt)),
                "completedAt must not be before startedAt");
        switch (status) {
            case PENDING -> requireState(startedAt == null && completedAt == null
                    && evaluationId == null && failure == null, "pending task contains terminal data");
            case RUNNING -> requireState(startedAt != null && completedAt == null
                    && evaluationId == null && failure == null, "running task has invalid timestamps or result");
            case SUCCEEDED -> requireState(startedAt != null && completedAt != null
                    && evaluationId != null && !evaluationId.isBlank() && failure == null,
                    "succeeded task requires only an evaluation result");
            case FAILED -> requireState(startedAt != null && completedAt != null
                    && evaluationId == null && failure != null, "failed task requires only a failure result");
        }
    }

    public static UnderwritingTask pending(String id, EvaluationRequest request, Instant now) {
        return new UnderwritingTask(id, request, UnderwritingTaskStatus.PENDING, null, null, now, null, null);
    }

    public UnderwritingTask start(Instant now) {
        requireStatus(UnderwritingTaskStatus.PENDING);
        return new UnderwritingTask(id, request, UnderwritingTaskStatus.RUNNING, null, null, createdAt, now, null);
    }

    public UnderwritingTask succeed(String completedEvaluationId, Instant now) {
        requireStatus(UnderwritingTaskStatus.RUNNING);
        if (completedEvaluationId == null || completedEvaluationId.isBlank()) {
            throw new IllegalArgumentException("evaluationId must not be blank");
        }
        return new UnderwritingTask(id, request, UnderwritingTaskStatus.SUCCEEDED, completedEvaluationId, null,
                createdAt, startedAt, now);
    }

    public UnderwritingTask fail(UnderwritingTaskFailure taskFailure, Instant now) {
        requireStatus(UnderwritingTaskStatus.RUNNING);
        return new UnderwritingTask(id, request, UnderwritingTaskStatus.FAILED, null,
                Objects.requireNonNull(taskFailure, "failure must not be null"), createdAt, startedAt, now);
    }

    public boolean terminal() {
        return status == UnderwritingTaskStatus.SUCCEEDED || status == UnderwritingTaskStatus.FAILED;
    }

    private void requireStatus(UnderwritingTaskStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Task %s cannot transition from %s; expected %s"
                    .formatted(id, status, expected));
        }
    }

    private static void requireState(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
