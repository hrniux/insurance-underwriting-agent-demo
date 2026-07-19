package com.hrniux.underwriting.rag;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryKnowledgeVersionRepository implements KnowledgeVersionRepository {

    private final ConcurrentHashMap<VersionKey, KnowledgeDocumentVersion> versions = new ConcurrentHashMap<>();

    @Override
    public KnowledgeDocumentVersion save(KnowledgeDocumentVersion version) {
        versions.put(new VersionKey(version.documentId(), version.version()), version);
        return version;
    }

    @Override
    public Optional<KnowledgeDocumentVersion> findByDocumentIdAndVersion(String documentId, int version) {
        return Optional.ofNullable(versions.get(new VersionKey(documentId, version)));
    }

    @Override
    public List<KnowledgeDocumentVersion> findByDocumentId(String documentId) {
        return versions.values().stream()
                .filter(version -> version.documentId().equals(documentId))
                .sorted(Comparator.comparingInt(KnowledgeDocumentVersion::version))
                .toList();
    }

    private record VersionKey(String documentId, int version) {
    }
}
