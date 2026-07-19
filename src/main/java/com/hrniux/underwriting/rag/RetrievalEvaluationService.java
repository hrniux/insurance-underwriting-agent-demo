package com.hrniux.underwriting.rag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class RetrievalEvaluationService {

    private static final int MAX_CASES = 100;
    private static final int MAX_TOP_K = 20;

    private final KnowledgeService knowledge;

    public RetrievalEvaluationService(KnowledgeService knowledge) {
        this.knowledge = Objects.requireNonNull(knowledge, "knowledge must not be null");
    }

    public RetrievalEvaluationReport evaluate(
            List<RetrievalEvaluationCase> cases,
            int topK,
            double minimumRecallAtK,
            double minimumMeanReciprocalRank) {
        Objects.requireNonNull(cases, "cases must not be null");
        if (cases.isEmpty() || cases.size() > MAX_CASES) {
            throw new IllegalArgumentException("cases must contain between 1 and 100 entries");
        }
        if (topK <= 0 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK must be between 1 and 20");
        }
        requireRatio(minimumRecallAtK, "minimumRecallAtK");
        requireRatio(minimumMeanReciprocalRank, "minimumMeanReciprocalRank");

        List<RetrievalEvaluationCaseResult> results = cases.stream()
                .map(testCase -> evaluate(testCase, topK))
                .toList();
        double recallAtK = results.stream()
                .mapToDouble(RetrievalEvaluationCaseResult::recallAtK)
                .average()
                .orElse(0.0);
        double meanReciprocalRank = results.stream()
                .mapToDouble(RetrievalEvaluationCaseResult::reciprocalRank)
                .average()
                .orElse(0.0);
        boolean passed = recallAtK >= minimumRecallAtK
                && meanReciprocalRank >= minimumMeanReciprocalRank;
        return new RetrievalEvaluationReport(
                topK,
                results.size(),
                recallAtK,
                meanReciprocalRank,
                minimumRecallAtK,
                minimumMeanReciprocalRank,
                passed,
                results);
    }

    private RetrievalEvaluationCaseResult evaluate(RetrievalEvaluationCase testCase, int topK) {
        List<String> retrievedDocumentIds = knowledge.search(
                        testCase.query(), topK, testCase.documentType(), testCase.productCode()).stream()
                .map(hit -> hit.chunk().documentId())
                .toList();
        Set<String> expected = new LinkedHashSet<>(testCase.expectedDocumentIds());
        Set<String> retrievedRelevant = new LinkedHashSet<>(retrievedDocumentIds);
        retrievedRelevant.retainAll(expected);
        double recallAtK = (double) retrievedRelevant.size() / expected.size();

        Integer firstRelevantRank = null;
        for (int index = 0; index < retrievedDocumentIds.size(); index++) {
            if (expected.contains(retrievedDocumentIds.get(index))) {
                firstRelevantRank = index + 1;
                break;
            }
        }
        double reciprocalRank = firstRelevantRank == null ? 0.0 : 1.0 / firstRelevantRank;
        return new RetrievalEvaluationCaseResult(
                testCase.name(),
                testCase.query(),
                testCase.expectedDocumentIds(),
                retrievedDocumentIds,
                recallAtK,
                firstRelevantRank,
                reciprocalRank);
    }

    private void requireRatio(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be between 0 and 1");
        }
    }
}
