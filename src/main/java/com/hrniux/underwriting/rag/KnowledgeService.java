package com.hrniux.underwriting.rag;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hrniux.underwriting.shared.config.AgentProperties;
import com.hrniux.underwriting.shared.error.ConflictException;
import com.hrniux.underwriting.shared.error.ResourceNotFoundException;

@Service
public class KnowledgeService {

    private final KnowledgeRepository repository;
    private final KnowledgeVersionRepository versions;
    private final TextDocumentParser parser;
    private final ParagraphTextSplitter splitter;
    private final VectorStore vectorStore;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int defaultTopK;
    private final Clock clock;

    @Autowired
    public KnowledgeService(
            KnowledgeRepository repository,
            KnowledgeVersionRepository versions,
            TextDocumentParser parser,
            ParagraphTextSplitter splitter,
            VectorStore vectorStore,
            AgentProperties properties) {
        this(repository, versions, parser, splitter, vectorStore,
                properties.rag().chunkSize(),
                properties.rag().chunkOverlap(),
                properties.rag().topK(),
                Clock.systemUTC());
    }

    KnowledgeService(
            KnowledgeRepository repository,
            TextDocumentParser parser,
            ParagraphTextSplitter splitter,
            VectorStore vectorStore,
            int chunkSize,
            int chunkOverlap,
            int defaultTopK) {
        this(repository, new InMemoryKnowledgeVersionRepository(), parser, splitter, vectorStore,
                chunkSize, chunkOverlap, defaultTopK, Clock.systemUTC());
    }

    KnowledgeService(
            KnowledgeRepository repository,
            KnowledgeVersionRepository versions,
            TextDocumentParser parser,
            ParagraphTextSplitter splitter,
            VectorStore vectorStore,
            int chunkSize,
            int chunkOverlap,
            int defaultTopK,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.versions = Objects.requireNonNull(versions, "versions must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.splitter = Objects.requireNonNull(splitter, "splitter must not be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.defaultTopK = defaultTopK;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized KnowledgeIngestionResult ingest(KnowledgeDocument document) {
        KnowledgeDocumentVersion draft = createDraft(document);
        KnowledgePublicationResult publication = publish(draft.documentId(), draft.version());
        return new KnowledgeIngestionResult(publication.documentId(), publication.chunkCount());
    }

    public synchronized KnowledgeDocumentVersion createDraft(KnowledgeDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        if (!versions.findByDocumentId(document.id()).isEmpty()) {
            throw new ConflictException(
                    "KNOWLEDGE_DOCUMENT_EXISTS",
                    "Knowledge document already exists: " + document.id());
        }
        KnowledgeDocumentVersion draft = KnowledgeDocumentVersion.draft(document, 1, clock.instant());
        return versions.save(draft);
    }

    public synchronized KnowledgeDocumentVersion createVersion(String documentId, KnowledgeDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        if (!document.id().equals(documentId)) {
            throw new IllegalArgumentException("document id must match path documentId");
        }
        List<KnowledgeDocumentVersion> history = versions.findByDocumentId(documentId);
        if (history.isEmpty()) {
            throw new ResourceNotFoundException("KNOWLEDGE_DOCUMENT_NOT_FOUND", documentId);
        }
        if (history.stream().anyMatch(version -> version.status() == KnowledgeDocumentStatus.DRAFT)) {
            throw new ConflictException(
                    "KNOWLEDGE_DRAFT_EXISTS",
                    "Knowledge document already has a draft version: " + documentId);
        }
        int nextVersion = history.getLast().version() + 1;
        return versions.save(KnowledgeDocumentVersion.draft(document, nextVersion, clock.instant()));
    }

    public synchronized KnowledgePublicationResult publish(String documentId, int version) {
        KnowledgeDocumentVersion target = getVersion(documentId, version);
        if (target.status() != KnowledgeDocumentStatus.DRAFT) {
            throw invalidTransition(target, KnowledgeDocumentStatus.DRAFT, KnowledgeDocumentStatus.PUBLISHED);
        }

        KnowledgeDocument parsed = new KnowledgeDocument(
                target.document().id(),
                target.document().title(),
                target.document().type(),
                target.document().productCode(),
                parser.parse(target.document().content()),
                target.document().metadata());
        List<DocumentChunk> chunks = versionedChunks(
                splitter.split(parsed, chunkSize, chunkOverlap), target.version());
        Instant now = clock.instant();
        vectorStore.replaceDocument(documentId, chunks);
        versions.findByDocumentId(documentId).stream()
                .filter(existing -> existing.status() == KnowledgeDocumentStatus.PUBLISHED)
                .map(existing -> existing.retire(now))
                .forEach(versions::save);
        KnowledgeDocumentVersion published = target.publish(now);
        versions.save(published);
        repository.save(target.document());
        return new KnowledgePublicationResult(
                documentId, version, KnowledgeDocumentStatus.PUBLISHED, chunks.size());
    }

    public synchronized KnowledgeDocumentVersion retire(String documentId, int version) {
        KnowledgeDocumentVersion target = getVersion(documentId, version);
        if (target.status() != KnowledgeDocumentStatus.PUBLISHED) {
            throw invalidTransition(target, KnowledgeDocumentStatus.PUBLISHED, KnowledgeDocumentStatus.RETIRED);
        }
        KnowledgeDocumentVersion retired = target.retire(clock.instant());
        versions.save(retired);
        repository.deleteById(documentId);
        vectorStore.removeDocument(documentId);
        return retired;
    }

    public KnowledgeDocument get(String documentId) {
        return repository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("KNOWLEDGE_DOCUMENT_NOT_FOUND", documentId));
    }

    public List<KnowledgeDocument> list() {
        return repository.findAll();
    }

    public KnowledgeDocumentVersion getVersion(String documentId, int version) {
        return versions.findByDocumentIdAndVersion(documentId, version)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "KNOWLEDGE_VERSION_NOT_FOUND", documentId + "@" + version));
    }

    public List<KnowledgeDocumentVersion> versions(String documentId) {
        List<KnowledgeDocumentVersion> history = versions.findByDocumentId(documentId);
        if (history.isEmpty()) {
            throw new ResourceNotFoundException("KNOWLEDGE_DOCUMENT_NOT_FOUND", documentId);
        }
        return history;
    }

    public List<RetrievalHit> search(
            String query, int topK, DocumentType type, String productCode) {
        int requestedTopK = topK <= 0 ? defaultTopK : topK;
        return vectorStore.search(query, requestedTopK, type, productCode);
    }

    private List<DocumentChunk> versionedChunks(List<DocumentChunk> chunks, int version) {
        return chunks.stream().map(chunk -> {
            Map<String, String> metadata = new HashMap<>(chunk.metadata());
            metadata.put("knowledgeVersion", Integer.toString(version));
            return new DocumentChunk(
                    chunk.documentId() + "-V" + version + "-CHUNK-" + chunk.index(),
                    chunk.documentId(),
                    chunk.index(),
                    chunk.title(),
                    chunk.type(),
                    chunk.productCode(),
                    chunk.content(),
                    metadata);
        }).toList();
    }

    private ConflictException invalidTransition(
            KnowledgeDocumentVersion version,
            KnowledgeDocumentStatus expected,
            KnowledgeDocumentStatus target) {
        return new ConflictException(
                "INVALID_KNOWLEDGE_TRANSITION",
                "Knowledge document %s version %d cannot transition from %s to %s; expected %s"
                        .formatted(version.documentId(), version.version(), version.status(), target, expected));
    }
}
