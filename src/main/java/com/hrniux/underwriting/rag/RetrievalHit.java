package com.hrniux.underwriting.rag;

import java.util.List;
import java.util.Objects;

public record RetrievalHit(
        DocumentChunk chunk,
        double score,
        double vectorScore,
        double lexicalScore,
        RetrievalMode mode,
        List<String> matchedTerms) {

    public RetrievalHit {
        Objects.requireNonNull(chunk, "chunk must not be null");
        requireScore(score, "score");
        requireScore(vectorScore, "vectorScore");
        requireScore(lexicalScore, "lexicalScore");
        Objects.requireNonNull(mode, "mode must not be null");
        matchedTerms = List.copyOf(Objects.requireNonNull(matchedTerms, "matchedTerms must not be null"));
    }

    public RetrievalHit(DocumentChunk chunk, double score) {
        this(chunk, score, score, 0.0, RetrievalMode.VECTOR_ONLY, List.of());
    }

    private static void requireScore(double score, String field) {
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException(field + " must be between 0 and 1");
        }
    }
}
