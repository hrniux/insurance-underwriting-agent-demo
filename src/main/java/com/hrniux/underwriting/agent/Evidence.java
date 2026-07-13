package com.hrniux.underwriting.agent;

import java.util.Objects;

import com.hrniux.underwriting.rag.DocumentType;

public record Evidence(
        String documentId,
        String chunkId,
        String title,
        DocumentType type,
        String excerpt,
        double score) {

    public Evidence {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(excerpt, "excerpt must not be null");
    }
}
