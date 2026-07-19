package com.hrniux.underwriting.rag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record RetrievalEvaluationCase(
        String name,
        String query,
        List<String> expectedDocumentIds,
        DocumentType documentType,
        String productCode) {

    public RetrievalEvaluationCase {
        name = requireText(name, "name");
        query = requireText(query, "query");
        Objects.requireNonNull(expectedDocumentIds, "expectedDocumentIds must not be null");
        expectedDocumentIds = expectedDocumentIds.stream()
                .map(id -> requireText(id, "expectedDocumentId"))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new), List::copyOf));
        if (expectedDocumentIds.isEmpty()) {
            throw new IllegalArgumentException("expectedDocumentIds must not be empty");
        }
        productCode = productCode == null || productCode.isBlank()
                ? null
                : productCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
