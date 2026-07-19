package com.hrniux.underwriting.task;

import java.util.Objects;

public record UnderwritingTaskSubmissionResult(UnderwritingTask task, boolean replayed) {

    public UnderwritingTaskSubmissionResult {
        Objects.requireNonNull(task, "task must not be null");
    }
}
