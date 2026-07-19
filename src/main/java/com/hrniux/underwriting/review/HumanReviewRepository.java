package com.hrniux.underwriting.review;

import java.util.List;
import java.util.Optional;

public interface HumanReviewRepository {

    boolean create(HumanReview review);

    Optional<HumanReview> findByEvaluationId(String evaluationId);

    List<HumanReview> findAll();
}
