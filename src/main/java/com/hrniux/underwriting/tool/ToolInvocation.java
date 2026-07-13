package com.hrniux.underwriting.tool;

import java.util.Objects;

public record ToolInvocation<T>(T result, ToolCallTrace trace) {

    public ToolInvocation {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(trace, "trace must not be null");
    }
}
