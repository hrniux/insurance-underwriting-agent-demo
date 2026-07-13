package com.hrniux.underwriting.agent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryEvaluationRepository implements EvaluationRepository {

    private final ConcurrentHashMap<String, UnderwritingEvaluation> evaluations = new ConcurrentHashMap<>();

    @Override
    public UnderwritingEvaluation save(UnderwritingEvaluation evaluation) {
        evaluations.put(evaluation.id(), evaluation);
        return evaluation;
    }

    @Override
    public Optional<UnderwritingEvaluation> findById(String id) {
        return Optional.ofNullable(evaluations.get(id));
    }

    @Override
    public List<UnderwritingEvaluation> findAll() {
        return evaluations.values().stream()
                .sorted(Comparator.comparing(UnderwritingEvaluation::createdAt).reversed())
                .toList();
    }
}
