package com.hrniux.underwriting.rag;

import java.util.List;
import java.util.Optional;

public interface KnowledgeVersionRepository {

    KnowledgeDocumentVersion save(KnowledgeDocumentVersion version);

    Optional<KnowledgeDocumentVersion> findByDocumentIdAndVersion(String documentId, int version);

    List<KnowledgeDocumentVersion> findByDocumentId(String documentId);
}
