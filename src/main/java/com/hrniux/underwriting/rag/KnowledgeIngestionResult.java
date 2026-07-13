package com.hrniux.underwriting.rag;

public record KnowledgeIngestionResult(String documentId, int chunkCount) {

    public KnowledgeIngestionResult {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (chunkCount <= 0) {
            throw new IllegalArgumentException("chunkCount must be positive");
        }
    }
}
