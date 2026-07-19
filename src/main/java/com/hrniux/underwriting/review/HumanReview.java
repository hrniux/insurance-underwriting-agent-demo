package com.hrniux.underwriting.review;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record HumanReview(
        String id,
        String evaluationId,
        String reviewerId,
        HumanReviewOutcome outcome,
        AgentReviewRelationship relationship,
        String comment,
        List<String> conditions,
        Instant reviewedAt) {

    public HumanReview {
        id = requireText(id, "id");
        evaluationId = requireText(evaluationId, "evaluationId");
        reviewerId = requireText(reviewerId, "reviewerId");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(relationship, "relationship must not be null");
        comment = requireText(comment, "comment");
        conditions = List.copyOf(conditions);
        Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
