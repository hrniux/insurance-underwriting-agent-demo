package com.hrniux.underwriting.rag;

public record KnowledgePublicationResult(
        String documentId,
        int version,
        KnowledgeDocumentStatus status,
        int chunkCount) {

    public KnowledgePublicationResult {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (status != KnowledgeDocumentStatus.PUBLISHED) {
            throw new IllegalArgumentException("status must be PUBLISHED");
        }
        if (chunkCount <= 0) {
            throw new IllegalArgumentException("chunkCount must be positive");
        }
    }
}
