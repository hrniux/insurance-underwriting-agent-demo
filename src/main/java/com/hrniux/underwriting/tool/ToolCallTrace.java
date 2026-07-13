package com.hrniux.underwriting.tool;

import java.time.Instant;
import java.util.Objects;

public record ToolCallTrace(
        ToolName toolName,
        Instant startedAt,
        long durationMs,
        ToolCallStatus status,
        String inputSummary,
        String outputSummary,
        String errorCode) {

    public ToolCallTrace {
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(inputSummary, "inputSummary must not be null");
        Objects.requireNonNull(outputSummary, "outputSummary must not be null");
        durationMs = Math.max(0, durationMs);
    }
}
