package com.hrniux.underwriting.review;

import java.util.List;

public record HumanReviewCommand(
        String reviewerId,
        HumanReviewOutcome outcome,
        String comment,
        List<String> conditions) {

    public HumanReviewCommand {
        reviewerId = requireText(reviewerId, "reviewerId");
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        comment = requireText(comment, "comment");
        conditions = conditions == null
                ? List.of()
                : conditions.stream().map(value -> requireText(value, "condition")).toList();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
