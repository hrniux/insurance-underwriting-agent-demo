package com.hrniux.underwriting.agent;

import java.util.List;
import java.util.Optional;

public interface EvaluationRepository {

    UnderwritingEvaluation save(UnderwritingEvaluation evaluation);

    Optional<UnderwritingEvaluation> findById(String id);

    List<UnderwritingEvaluation> findAll();
}
