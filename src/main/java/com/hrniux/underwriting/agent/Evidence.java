package com.hrniux.underwriting.agent;

import java.util.List;
import java.util.Objects;

import com.hrniux.underwriting.rag.DocumentType;
import com.hrniux.underwriting.rag.RetrievalMode;

public record Evidence(
        String documentId,
        String chunkId,
        String title,
        DocumentType type,
        String excerpt,
        double score,
        Integer knowledgeVersion,
        Double vectorScore,
        Double lexicalScore,
        RetrievalMode retrievalMode,
        List<String> matchedTerms) {

    public Evidence {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(excerpt, "excerpt must not be null");
        requireScore(score, "score");
        knowledgeVersion = knowledgeVersion == null ? 0 : knowledgeVersion;
        if (knowledgeVersion < 0) {
            throw new IllegalArgumentException("knowledgeVersion must not be negative");
        }
        vectorScore = vectorScore == null ? 0.0 : vectorScore;
        lexicalScore = lexicalScore == null ? 0.0 : lexicalScore;
        requireScore(vectorScore, "vectorScore");
        requireScore(lexicalScore, "lexicalScore");
        retrievalMode = retrievalMode == null ? RetrievalMode.UNKNOWN : retrievalMode;
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
    }

    public Evidence(
            String documentId,
            String chunkId,
            String title,
            DocumentType type,
            String excerpt,
            double score) {
        this(documentId, chunkId, title, type, excerpt, score,
                0, score, 0.0, RetrievalMode.VECTOR_ONLY, List.of());
    }

    private static void requireScore(double score, String field) {
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException(field + " must be between 0 and 1");
        }
    }
}
