package com.hrniux.underwriting.rag;

import java.util.List;
import java.util.Optional;

public interface KnowledgeRepository {

    KnowledgeDocument save(KnowledgeDocument document);

    Optional<KnowledgeDocument> findById(String id);

    List<KnowledgeDocument> findAll();

    boolean existsById(String id);

    void deleteById(String id);
}
