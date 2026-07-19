package com.hrniux.underwriting.agent;

import java.util.Objects;

public record EvaluationSubmissionResult(UnderwritingEvaluation evaluation, boolean replayed) {

    public EvaluationSubmissionResult {
        Objects.requireNonNull(evaluation, "evaluation must not be null");
    }
}
