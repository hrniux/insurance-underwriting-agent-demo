package com.hrniux.underwriting.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryVectorStore implements VectorStore {

    private static final double VECTOR_WEIGHT = 0.65;
    private static final double LEXICAL_WEIGHT = 0.35;
    private static final double BM25_K1 = 1.2;
    private static final double BM25_B = 0.75;
    private static final int MAX_EXPLAINED_TERMS = 12;

    private final EmbeddingService embeddingService;
    private final ConcurrentHashMap<String, StoredChunk> chunks = new ConcurrentHashMap<>();

    public InMemoryVectorStore(EmbeddingService embeddingService) {
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService must not be null");
    }

    @Override
    public void add(DocumentChunk chunk) {
        Objects.requireNonNull(chunk, "chunk must not be null");
        String searchableText = "%s %s %s %s".formatted(
                chunk.documentId(), chunk.title(), chunk.content(), String.join(" ", chunk.metadata().values()));
        List<String> terms = SearchTextAnalyzer.tokenize(searchableText);
        Map<String, Long> termFrequencies = terms.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        chunks.put(chunk.id(), new StoredChunk(
                chunk,
                embeddingService.embed(chunk.title() + "\n" + chunk.content()),
                termFrequencies,
                terms.size()));
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
        Set<String> queryTerms = new LinkedHashSet<>(SearchTextAnalyzer.tokenize(query));
        String normalizedProduct = productCode == null ? null : productCode.toUpperCase(Locale.ROOT);
        List<StoredChunk> candidates = chunks.values().stream()
                .filter(stored -> type == null || stored.chunk().type() == type)
                .filter(stored -> normalizedProduct == null
                        || stored.chunk().productCode().equals(normalizedProduct))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        double averageLength = candidates.stream().mapToInt(StoredChunk::termCount).average().orElse(1.0);
        Map<String, Long> documentFrequencies = queryTerms.stream().collect(Collectors.toMap(
                Function.identity(),
                term -> candidates.stream().filter(candidate -> candidate.contains(term)).count()));
        List<ScoredChunk> scored = candidates.stream()
                .map(candidate -> score(candidate, queryVector, queryTerms,
                        documentFrequencies, candidates.size(), averageLength))
                .toList();
        double maximumLexicalScore = scored.stream()
                .mapToDouble(ScoredChunk::rawLexicalScore)
                .max()
                .orElse(0.0);

        return scored.stream()
                .map(candidate -> candidate.toHit(maximumLexicalScore))
                .filter(hit -> hit.score() > 0.0)
                .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed()
                        .thenComparing(Comparator.comparingDouble(RetrievalHit::lexicalScore).reversed())
                        .thenComparing(hit -> hit.chunk().id()))
                .limit(topK)
                .toList();
    }

    @Override
    public int size() {
        return chunks.size();
    }

    private ScoredChunk score(
            StoredChunk candidate,
            double[] queryVector,
            Set<String> queryTerms,
            Map<String, Long> documentFrequencies,
            int candidateCount,
            double averageLength) {
        double vectorScore = clamp(dotProduct(queryVector, candidate.vector()));
        double lexicalScore = 0.0;
        List<String> matchedTerms = new ArrayList<>();
        for (String term : queryTerms) {
            long frequency = candidate.frequency(term);
            if (frequency == 0) {
                continue;
            }
            matchedTerms.add(term);
            long documentFrequency = documentFrequencies.getOrDefault(term, 0L);
            double inverseDocumentFrequency = Math.log(
                    1.0 + (candidateCount - documentFrequency + 0.5) / (documentFrequency + 0.5));
            double lengthRatio = candidate.termCount() / Math.max(averageLength, 1.0);
            double denominator = frequency + BM25_K1 * (1.0 - BM25_B + BM25_B * lengthRatio);
            lexicalScore += inverseDocumentFrequency * frequency * (BM25_K1 + 1.0) / denominator;
        }
        List<String> explainedTerms = matchedTerms.stream()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Function.identity()))
                .limit(MAX_EXPLAINED_TERMS)
                .toList();
        return new ScoredChunk(candidate.chunk(), vectorScore, lexicalScore, explainedTerms);
    }

    private double dotProduct(double[] first, double[] second) {
        if (first.length != second.length) {
            throw new IllegalArgumentException("embedding dimensions must match");
        }
        double result = 0.0;
        for (int index = 0; index < first.length; index++) {
            result += first[index] * second[index];
        }
        return result;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record StoredChunk(
            DocumentChunk chunk,
            double[] vector,
            Map<String, Long> termFrequencies,
            int termCount) {

        private StoredChunk {
            vector = vector.clone();
            termFrequencies = Map.copyOf(termFrequencies);
        }

        @Override
        public double[] vector() {
            return vector.clone();
        }

        long frequency(String term) {
            return termFrequencies.getOrDefault(term, 0L);
        }

        boolean contains(String term) {
            return termFrequencies.containsKey(term);
        }
    }

    private record ScoredChunk(
            DocumentChunk chunk,
            double vectorScore,
            double rawLexicalScore,
            List<String> matchedTerms) {

        RetrievalHit toHit(double maximumLexicalScore) {
            double lexicalScore = maximumLexicalScore <= 0.0 ? 0.0 : rawLexicalScore / maximumLexicalScore;
            double score = clampScore(VECTOR_WEIGHT * vectorScore + LEXICAL_WEIGHT * lexicalScore);
            RetrievalMode mode = vectorScore > 0.0 && lexicalScore > 0.0
                    ? RetrievalMode.HYBRID
                    : vectorScore > 0.0 ? RetrievalMode.VECTOR_ONLY : RetrievalMode.LEXICAL_ONLY;
            return new RetrievalHit(chunk, score, vectorScore, lexicalScore, mode, matchedTerms);
        }

        private static double clampScore(double value) {
            return Math.max(0.0, Math.min(1.0, value));
        }
    }
}
