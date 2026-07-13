package com.hrniux.underwriting.agent;

import java.time.Instant;
import java.util.Objects;

public record StepTrace(
        AgentStep step,
        StepStatus status,
        Instant startedAt,
        long durationMs,
        String errorCode) {

    public StepTrace {
        Objects.requireNonNull(step, "step must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        durationMs = Math.max(0, durationMs);
    }
}
