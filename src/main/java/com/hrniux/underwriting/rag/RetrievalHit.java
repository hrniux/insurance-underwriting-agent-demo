package com.hrniux.underwriting.rag;

import java.util.Objects;

public record RetrievalHit(DocumentChunk chunk, double score) {

    public RetrievalHit {
        Objects.requireNonNull(chunk, "chunk must not be null");
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
    }
}
