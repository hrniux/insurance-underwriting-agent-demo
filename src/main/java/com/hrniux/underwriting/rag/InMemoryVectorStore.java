package com.hrniux.underwriting.rag;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryVectorStore implements VectorStore {

    private final EmbeddingService embeddingService;
    private final ConcurrentHashMap<String, StoredChunk> chunks = new ConcurrentHashMap<>();

    public InMemoryVectorStore(EmbeddingService embeddingService) {
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService must not be null");
    }

    @Override
    public void add(DocumentChunk chunk) {
        Objects.requireNonNull(chunk, "chunk must not be null");
        chunks.put(chunk.id(), new StoredChunk(chunk, embeddingService.embed(chunk.content())));
    }

    @Override
    public List<RetrievalHit> search(String query, int topK, DocumentType type, String productCode) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }

        double[] queryVector = embeddingService.embed(query);
        String normalizedProduct = productCode == null ? null : productCode.toUpperCase(Locale.ROOT);
        return chunks.values().stream()
                .filter(stored -> type == null || stored.chunk().type() == type)
                .filter(stored -> normalizedProduct == null
                        || stored.chunk().productCode().equals(normalizedProduct))
                .map(stored -> new RetrievalHit(stored.chunk(), dotProduct(queryVector, stored.vector())))
                .filter(hit -> hit.score() > 0.0)
                .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed()
                        .thenComparing(hit -> hit.chunk().id()))
                .limit(topK)
                .toList();
    }

    @Override
    public int size() {
        return chunks.size();
    }

    private double dotProduct(double[] first, double[] second) {
        double result = 0.0;
        for (int index = 0; index < first.length; index++) {
            result += first[index] * second[index];
        }
        return result;
    }

    private record StoredChunk(DocumentChunk chunk, double[] vector) {

        private StoredChunk {
            vector = vector.clone();
        }

        @Override
        public double[] vector() {
            return vector.clone();
        }
    }
}
