package com.hrniux.underwriting.rag;

import java.util.List;
import java.util.Objects;

public record RetrievalEvaluationCaseResult(
        String name,
        String query,
        List<String> expectedDocumentIds,
        List<String> retrievedDocumentIds,
        double recallAtK,
        Integer firstRelevantRank,
        double reciprocalRank) {

    public RetrievalEvaluationCaseResult {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(query, "query must not be null");
        expectedDocumentIds = List.copyOf(
                Objects.requireNonNull(expectedDocumentIds, "expectedDocumentIds must not be null"));
        retrievedDocumentIds = List.copyOf(
                Objects.requireNonNull(retrievedDocumentIds, "retrievedDocumentIds must not be null"));
        requireRatio(recallAtK, "recallAtK");
        requireRatio(reciprocalRank, "reciprocalRank");
        if (firstRelevantRank != null && firstRelevantRank <= 0) {
            throw new IllegalArgumentException("firstRelevantRank must be positive");
        }
    }

    private static void requireRatio(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be between 0 and 1");
        }
    }
}
