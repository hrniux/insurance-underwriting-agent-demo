package com.hrniux.underwriting.rag;

import java.util.List;
import java.util.Objects;

public record RetrievalEvaluationReport(
        int topK,
        int caseCount,
        double recallAtK,
        double meanReciprocalRank,
        double minimumRecallAtK,
        double minimumMeanReciprocalRank,
        boolean passed,
        List<RetrievalEvaluationCaseResult> cases) {

    public RetrievalEvaluationReport {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        if (caseCount <= 0) {
            throw new IllegalArgumentException("caseCount must be positive");
        }
        requireRatio(recallAtK, "recallAtK");
        requireRatio(meanReciprocalRank, "meanReciprocalRank");
        requireRatio(minimumRecallAtK, "minimumRecallAtK");
        requireRatio(minimumMeanReciprocalRank, "minimumMeanReciprocalRank");
        cases = List.copyOf(Objects.requireNonNull(cases, "cases must not be null"));
        if (cases.size() != caseCount) {
            throw new IllegalArgumentException("caseCount must match cases size");
        }
    }

    private static void requireRatio(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be between 0 and 1");
        }
    }
}
