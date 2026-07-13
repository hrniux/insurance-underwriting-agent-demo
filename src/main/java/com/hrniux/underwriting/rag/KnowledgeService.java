package com.hrniux.underwriting.rag;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hrniux.underwriting.shared.config.AgentProperties;
import com.hrniux.underwriting.shared.error.ConflictException;
import com.hrniux.underwriting.shared.error.ResourceNotFoundException;

@Service
public class KnowledgeService {

    private final KnowledgeRepository repository;
    private final TextDocumentParser parser;
    private final ParagraphTextSplitter splitter;
    private final VectorStore vectorStore;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int defaultTopK;

    @Autowired
    public KnowledgeService(
            KnowledgeRepository repository,
            TextDocumentParser parser,
            ParagraphTextSplitter splitter,
            VectorStore vectorStore,
            AgentProperties properties) {
        this(repository, parser, splitter, vectorStore,
                properties.rag().chunkSize(),
                properties.rag().chunkOverlap(),
                properties.rag().topK());
    }

    KnowledgeService(
            KnowledgeRepository repository,
            TextDocumentParser parser,
            ParagraphTextSplitter splitter,
            VectorStore vectorStore,
            int chunkSize,
            int chunkOverlap,
            int defaultTopK) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.splitter = Objects.requireNonNull(splitter, "splitter must not be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.defaultTopK = defaultTopK;
    }

    public synchronized KnowledgeIngestionResult ingest(KnowledgeDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        if (repository.existsById(document.id())) {
            throw new ConflictException(
                    "KNOWLEDGE_DOCUMENT_EXISTS",
                    "Knowledge document already exists: " + document.id());
        }

        KnowledgeDocument parsed = new KnowledgeDocument(
                document.id(),
                document.title(),
                document.type(),
                document.productCode(),
                parser.parse(document.content()),
                document.metadata());
        List<DocumentChunk> chunks = splitter.split(parsed, chunkSize, chunkOverlap);
        chunks.forEach(vectorStore::add);
        repository.save(document);
        return new KnowledgeIngestionResult(document.id(), chunks.size());
    }

    public KnowledgeDocument get(String documentId) {
        return repository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("KNOWLEDGE_DOCUMENT_NOT_FOUND", documentId));
    }

    public List<KnowledgeDocument> list() {
        return repository.findAll();
    }

    public List<RetrievalHit> search(
            String query, int topK, DocumentType type, String productCode) {
        int requestedTopK = topK <= 0 ? defaultTopK : topK;
        return vectorStore.search(query, requestedTopK, type, productCode);
    }
}
