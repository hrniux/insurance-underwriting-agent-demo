package com.hrniux.underwriting.rag;

import java.util.Map;
import java.util.Objects;

public record DocumentChunk(
        String id,
        String documentId,
        int index,
        String title,
        DocumentType type,
        String productCode,
        String content,
        Map<String, String> metadata) {

    public DocumentChunk {
        id = requireText(id, "id");
        documentId = requireText(documentId, "documentId");
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        title = requireText(title, "title");
        Objects.requireNonNull(type, "type must not be null");
        productCode = requireText(productCode, "productCode").toUpperCase(java.util.Locale.ROOT);
        content = requireText(content, "content");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
