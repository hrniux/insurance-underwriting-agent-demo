package com.hrniux.underwriting.rag;

import java.time.Instant;
import java.util.Objects;

public record KnowledgeDocumentVersion(
        String documentId,
        int version,
        KnowledgeDocument document,
        KnowledgeDocumentStatus status,
        Instant createdAt,
        Instant publishedAt,
        Instant retiredAt) {

    public KnowledgeDocumentVersion {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        documentId = documentId.trim();
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(document, "document must not be null");
        if (!documentId.equals(document.id())) {
            throw new IllegalArgumentException("documentId must match document id");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (publishedAt != null && publishedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("publishedAt must not be before createdAt");
        }
        if (retiredAt != null && (publishedAt == null || retiredAt.isBefore(publishedAt))) {
            throw new IllegalArgumentException("retiredAt must not be before publishedAt");
        }
        switch (status) {
            case DRAFT -> requireState(publishedAt == null && retiredAt == null,
                    "draft version cannot have publication timestamps");
            case PUBLISHED -> requireState(publishedAt != null && retiredAt == null,
                    "published version requires only publishedAt");
            case RETIRED -> requireState(publishedAt != null && retiredAt != null,
                    "retired version requires publishedAt and retiredAt");
        }
    }

    public static KnowledgeDocumentVersion draft(KnowledgeDocument document, int version, Instant now) {
        return new KnowledgeDocumentVersion(
                document.id(), version, document, KnowledgeDocumentStatus.DRAFT, now, null, null);
    }

    public KnowledgeDocumentVersion publish(Instant now) {
        requireStatus(KnowledgeDocumentStatus.DRAFT);
        return new KnowledgeDocumentVersion(
                documentId, version, document, KnowledgeDocumentStatus.PUBLISHED, createdAt, now, null);
    }

    public KnowledgeDocumentVersion retire(Instant now) {
        requireStatus(KnowledgeDocumentStatus.PUBLISHED);
        return new KnowledgeDocumentVersion(
                documentId, version, document, KnowledgeDocumentStatus.RETIRED, createdAt, publishedAt, now);
    }

    private void requireStatus(KnowledgeDocumentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Knowledge document %s version %d cannot transition from %s; expected %s"
                    .formatted(documentId, version, status, expected));
        }
    }

    private static void requireState(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
