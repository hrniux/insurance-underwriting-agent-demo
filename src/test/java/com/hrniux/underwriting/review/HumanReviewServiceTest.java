package com.hrniux.underwriting.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.agent.EvaluationRepository;
import com.hrniux.underwriting.agent.UnderwritingEvaluation;
import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.shared.error.ConflictException;
import com.hrniux.underwriting.shared.error.ResourceNotFoundException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class HumanReviewServiceTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-07-19T10:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-07-19T10:10:00Z");

    @Test
    void recordsAnImmutableManualReviewResolutionAndLowCardinalityMetrics() {
        Fixture fixture = fixture(Decision.MANUAL_REVIEW);

        HumanReview review = fixture.service().submit("EVAL-1", new HumanReviewCommand(
                " UW-DEMO-001 ",
                HumanReviewOutcome.APPROVED,
                " 整改材料已核验 ",
                List.of(" 提高免赔额 ", "季度复查")));

        assertThat(review.id()).isEqualTo("REVIEW-1");
        assertThat(review.evaluationId()).isEqualTo("EVAL-1");
        assertThat(review.reviewerId()).isEqualTo("UW-DEMO-001");
        assertThat(review.outcome()).isEqualTo(HumanReviewOutcome.APPROVED);
        assertThat(review.relationship()).isEqualTo(AgentReviewRelationship.RESOLVED_MANUAL_REVIEW);
        assertThat(review.comment()).isEqualTo("整改材料已核验");
        assertThat(review.conditions()).containsExactly("提高免赔额", "季度复查");
        assertThat(review.reviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(fixture.service().get("EVAL-1")).isEqualTo(review);
        assertThat(fixture.service().list()).containsExactly(review);
        assertThat(fixture.metrics().get("underwriting.human.reviews")
                .tags("outcome", "APPROVED", "relationship", "RESOLVED_MANUAL_REVIEW")
                .counter().count()).isEqualTo(1);
        assertThat(fixture.metrics().get("underwriting.human.review.delay")
                .tags("outcome", "APPROVED", "relationship", "RESOLVED_MANUAL_REVIEW")
                .timer().totalTime(java.util.concurrent.TimeUnit.MINUTES)).isEqualTo(10);
    }

    @Test
    void rejectsASecondReviewWithoutDoubleCountingMetrics() {
        Fixture fixture = fixture(Decision.APPROVE);
        HumanReviewCommand command = new HumanReviewCommand(
                "UW-DEMO-001", HumanReviewOutcome.APPROVED, "确认自动通过建议", List.of());

        fixture.service().submit("EVAL-1", command);

        assertThatThrownBy(() -> fixture.service().submit("EVAL-1", command))
                .isInstanceOf(ConflictException.class)
                .satisfies(error -> assertThat(((ConflictException) error).errorCode())
                        .isEqualTo("HUMAN_REVIEW_ALREADY_EXISTS"));
        assertThat(fixture.metrics().get("underwriting.human.reviews")
                .tags("outcome", "APPROVED", "relationship", "CONFIRMED")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void atomicallyAcceptsOnlyOneConcurrentReview() throws Exception {
        Fixture fixture = fixture(Decision.MANUAL_REVIEW);
        HumanReviewCommand command = new HumanReviewCommand(
                "UW-DEMO-001", HumanReviewOutcome.APPROVED, "并发提交测试", List.of());
        List<Callable<Boolean>> submissions = java.util.stream.IntStream.range(0, 16)
                .mapToObj(ignored -> (Callable<Boolean>) () -> {
                    try {
                        fixture.service().submit("EVAL-1", command);
                        return true;
                    }
                    catch (ConflictException conflict) {
                        return false;
                    }
                })
                .toList();

        try (var executor = Executors.newFixedThreadPool(8)) {
            long accepted = executor.invokeAll(submissions).stream()
                    .filter(result -> {
                        try {
                            return result.get();
                        }
                        catch (Exception error) {
                            throw new AssertionError(error);
                        }
                    })
                    .count();

            assertThat(accepted).isOne();
        }
        assertThat(fixture.service().list()).hasSize(1);
        assertThat(fixture.metrics().get("underwriting.human.reviews")
                .tags("outcome", "APPROVED", "relationship", "RESOLVED_MANUAL_REVIEW")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void distinguishesConfirmationOverrideAndContinuedManualReview() {
        assertThat(submitRelationship(Decision.APPROVE, HumanReviewOutcome.APPROVED))
                .isEqualTo(AgentReviewRelationship.CONFIRMED);
        assertThat(submitRelationship(Decision.REJECT, HumanReviewOutcome.APPROVED))
                .isEqualTo(AgentReviewRelationship.OVERRIDDEN);
        assertThat(submitRelationship(Decision.MANUAL_REVIEW, HumanReviewOutcome.MORE_INFORMATION_REQUIRED))
                .isEqualTo(AgentReviewRelationship.CONTINUED_MANUAL_REVIEW);
    }

    @Test
    void differentiatesAMissingEvaluationFromAMissingReview() {
        EvaluationRepository evaluations = mock(EvaluationRepository.class);
        when(evaluations.findById("EVAL-MISSING")).thenReturn(Optional.empty());
        HumanReviewService service = service(evaluations, new InMemoryHumanReviewRepository(),
                new SimpleMeterRegistry());

        assertThatThrownBy(() -> service.submit("EVAL-MISSING", new HumanReviewCommand(
                "UW-DEMO-001", HumanReviewOutcome.REJECTED, "拒绝承保", List.of())))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(error -> assertThat(((ResourceNotFoundException) error).errorCode())
                        .isEqualTo("EVALUATION_NOT_FOUND"));

        Fixture fixture = fixture(Decision.MANUAL_REVIEW);
        assertThatThrownBy(() -> fixture.service().get("EVAL-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(error -> assertThat(((ResourceNotFoundException) error).errorCode())
                        .isEqualTo("HUMAN_REVIEW_NOT_FOUND"));
    }

    private AgentReviewRelationship submitRelationship(Decision decision, HumanReviewOutcome outcome) {
        Fixture fixture = fixture(decision);
        return fixture.service().submit("EVAL-1", new HumanReviewCommand(
                "UW-DEMO-001", outcome, "测试关系分类", List.of())).relationship();
    }

    private Fixture fixture(Decision decision) {
        EvaluationRepository evaluations = mock(EvaluationRepository.class);
        UnderwritingEvaluation evaluation = mock(UnderwritingEvaluation.class);
        when(evaluation.id()).thenReturn("EVAL-1");
        when(evaluation.decision()).thenReturn(decision);
        when(evaluation.createdAt()).thenReturn(EVALUATED_AT);
        when(evaluations.findById("EVAL-1")).thenReturn(Optional.of(evaluation));
        InMemoryHumanReviewRepository reviews = new InMemoryHumanReviewRepository();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        return new Fixture(service(evaluations, reviews, metrics), metrics);
    }

    private HumanReviewService service(
            EvaluationRepository evaluations,
            HumanReviewRepository reviews,
            SimpleMeterRegistry metrics) {
        return new HumanReviewService(
                evaluations,
                reviews,
                metrics,
                Clock.fixed(REVIEWED_AT, ZoneOffset.UTC),
                () -> "REVIEW-1");
    }

    private record Fixture(HumanReviewService service, SimpleMeterRegistry metrics) {
    }
}
