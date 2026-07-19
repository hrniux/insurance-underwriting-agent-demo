package com.hrniux.underwriting.review;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hrniux.underwriting.agent.EvaluationRepository;
import com.hrniux.underwriting.agent.UnderwritingEvaluation;
import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.shared.error.ConflictException;
import com.hrniux.underwriting.shared.error.ResourceNotFoundException;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class HumanReviewService {

    private static final String REVIEW_METRIC = "underwriting.human.reviews";
    private static final String REVIEW_DELAY_METRIC = "underwriting.human.review.delay";

    private final EvaluationRepository evaluations;
    private final HumanReviewRepository reviews;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final Supplier<String> idSupplier;

    @Autowired
    public HumanReviewService(
            EvaluationRepository evaluations,
            HumanReviewRepository reviews,
            MeterRegistry metrics) {
        this(evaluations, reviews, metrics, Clock.systemUTC(),
                () -> "REVIEW-" + UUID.randomUUID().toString().replace("-", ""));
    }

    HumanReviewService(
            EvaluationRepository evaluations,
            HumanReviewRepository reviews,
            MeterRegistry metrics,
            Clock clock,
            Supplier<String> idSupplier) {
        this.evaluations = evaluations;
        this.reviews = reviews;
        this.metrics = metrics;
        this.clock = clock;
        this.idSupplier = idSupplier;
    }

    public HumanReview submit(String evaluationId, HumanReviewCommand command) {
        UnderwritingEvaluation evaluation = requiredEvaluation(evaluationId);
        Instant reviewedAt = clock.instant();
        AgentReviewRelationship relationship = relationship(evaluation.decision(), command.outcome());
        HumanReview review = new HumanReview(
                idSupplier.get(),
                evaluation.id(),
                command.reviewerId(),
                command.outcome(),
                relationship,
                command.comment(),
                command.conditions(),
                reviewedAt);

        if (!reviews.create(review)) {
            throw new ConflictException(
                    "HUMAN_REVIEW_ALREADY_EXISTS",
                    "A human review has already been recorded for this evaluation");
        }

        metrics.counter(
                REVIEW_METRIC,
                "outcome", review.outcome().name(),
                "relationship", review.relationship().name()).increment();
        Duration delay = Duration.between(evaluation.createdAt(), reviewedAt);
        metrics.timer(
                REVIEW_DELAY_METRIC,
                "outcome", review.outcome().name(),
                "relationship", review.relationship().name())
                .record(delay.isNegative() ? Duration.ZERO : delay);
        return review;
    }

    public HumanReview get(String evaluationId) {
        requiredEvaluation(evaluationId);
        return reviews.findByEvaluationId(evaluationId)
                .orElseThrow(() -> new ResourceNotFoundException("HUMAN_REVIEW_NOT_FOUND", evaluationId));
    }

    public Optional<HumanReview> find(String evaluationId) {
        return reviews.findByEvaluationId(evaluationId);
    }

    public List<HumanReview> list() {
        return reviews.findAll();
    }

    private UnderwritingEvaluation requiredEvaluation(String evaluationId) {
        return evaluations.findById(evaluationId)
                .orElseThrow(() -> new ResourceNotFoundException("EVALUATION_NOT_FOUND", evaluationId));
    }

    private AgentReviewRelationship relationship(Decision decision, HumanReviewOutcome outcome) {
        return switch (decision) {
            case APPROVE -> outcome == HumanReviewOutcome.APPROVED
                    ? AgentReviewRelationship.CONFIRMED
                    : AgentReviewRelationship.OVERRIDDEN;
            case REJECT -> outcome == HumanReviewOutcome.REJECTED
                    ? AgentReviewRelationship.CONFIRMED
                    : AgentReviewRelationship.OVERRIDDEN;
            case MANUAL_REVIEW -> outcome == HumanReviewOutcome.MORE_INFORMATION_REQUIRED
                    ? AgentReviewRelationship.CONTINUED_MANUAL_REVIEW
                    : AgentReviewRelationship.RESOLVED_MANUAL_REVIEW;
        };
    }
}
