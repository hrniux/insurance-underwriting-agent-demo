package com.hrniux.underwriting.rag;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryKnowledgeRepository implements KnowledgeRepository {

    private final ConcurrentHashMap<String, KnowledgeDocument> documents = new ConcurrentHashMap<>();

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        documents.put(document.id(), document);
        return document;
    }

    @Override
    public Optional<KnowledgeDocument> findById(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    @Override
    public List<KnowledgeDocument> findAll() {
        return documents.values().stream()
                .sorted(Comparator.comparing(KnowledgeDocument::id))
                .toList();
    }

    @Override
    public boolean existsById(String id) {
        return documents.containsKey(id);
    }

    @Override
    public void deleteById(String id) {
        documents.remove(id);
    }
}
