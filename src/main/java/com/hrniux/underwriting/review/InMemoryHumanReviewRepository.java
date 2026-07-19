package com.hrniux.underwriting.review;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryHumanReviewRepository implements HumanReviewRepository {

    private final ConcurrentHashMap<String, HumanReview> reviewsByEvaluation = new ConcurrentHashMap<>();

    @Override
    public boolean create(HumanReview review) {
        return reviewsByEvaluation.putIfAbsent(review.evaluationId(), review) == null;
    }

    @Override
    public Optional<HumanReview> findByEvaluationId(String evaluationId) {
        return Optional.ofNullable(reviewsByEvaluation.get(evaluationId));
    }

    @Override
    public List<HumanReview> findAll() {
        return reviewsByEvaluation.values().stream()
                .sorted(Comparator.comparing(HumanReview::reviewedAt).reversed())
                .toList();
    }
}
