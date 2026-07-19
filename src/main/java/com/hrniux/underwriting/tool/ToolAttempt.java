package com.hrniux.underwriting.tool;

import java.util.Objects;

public record ToolAttempt<T>(T result, ToolCallTrace trace, RuntimeException failure) {

    public ToolAttempt {
        Objects.requireNonNull(trace, "trace must not be null");
        if ((result == null) == (failure == null)) {
            throw new IllegalArgumentException("exactly one of result or failure must be present");
        }
    }

    public boolean succeeded() {
        return failure == null;
    }

    public boolean failed() {
        return failure != null;
    }

    public ToolInvocation<T> requiredInvocation() {
        if (failure != null) {
            throw failure;
        }
        return new ToolInvocation<>(result, trace);
    }
}
