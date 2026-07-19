package com.hrniux.underwriting.agent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hrniux.underwriting.shared.config.IdempotencyProperties;
import com.hrniux.underwriting.shared.error.ConflictException;
import com.hrniux.underwriting.shared.error.InvalidRequestException;
import com.hrniux.underwriting.shared.error.ServiceCapacityException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class EvaluationSubmissionService {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String REPLAY_HEADER = "Idempotency-Replayed";

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final String SUBMISSION_METRIC = "underwriting.evaluation.submissions";
    private static final String DURATION_METRIC = "underwriting.evaluation.duration";
    private static final String DECISION_METRIC = "underwriting.evaluation.decisions";

    private final UnderwritingAgentOrchestrator orchestrator;
    private final IdempotencyProperties properties;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final Map<String, Slot> slots = new HashMap<>();

    @Autowired
    public EvaluationSubmissionService(
            UnderwritingAgentOrchestrator orchestrator,
            IdempotencyProperties properties,
            MeterRegistry metrics) {
        this(orchestrator, properties, metrics, Clock.systemUTC());
    }

    EvaluationSubmissionService(
            UnderwritingAgentOrchestrator orchestrator,
            IdempotencyProperties properties,
            MeterRegistry metrics,
            Clock clock) {
        this.orchestrator = orchestrator;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public EvaluationSubmissionResult submit(EvaluationRequest request, String rawIdempotencyKey) {
        Timer.Sample timer = Timer.start(metrics);
        SubmissionOutcome outcome = SubmissionOutcome.FAILED;
        try {
            String idempotencyKey = normalizeKey(rawIdempotencyKey);
            if (idempotencyKey == null) {
                UnderwritingEvaluation evaluation = createEvaluation(request);
                outcome = SubmissionOutcome.CREATED;
                return new EvaluationSubmissionResult(evaluation, false);
            }

            Claim claim = claim(idempotencyKey, fingerprint(request));
            if (!claim.owner()) {
                UnderwritingEvaluation evaluation = await(claim.slot());
                outcome = SubmissionOutcome.REPLAYED;
                return new EvaluationSubmissionResult(evaluation, true);
            }

            try {
                UnderwritingEvaluation evaluation = createEvaluation(request);
                complete(claim.slot(), evaluation);
                outcome = SubmissionOutcome.CREATED;
                return new EvaluationSubmissionResult(evaluation, false);
            }
            catch (RuntimeException | Error error) {
                fail(idempotencyKey, claim.slot(), error);
                throw error;
            }
        }
        catch (ConflictException conflict) {
            outcome = SubmissionOutcome.CONFLICT;
            throw conflict;
        }
        finally {
            String outcomeTag = outcome.name().toLowerCase(Locale.ROOT);
            metrics.counter(SUBMISSION_METRIC, "outcome", outcomeTag).increment();
            timer.stop(metrics.timer(DURATION_METRIC, "outcome", outcomeTag));
        }
    }

    private UnderwritingEvaluation createEvaluation(EvaluationRequest request) {
        UnderwritingEvaluation evaluation = orchestrator.evaluate(request);
        metrics.counter(DECISION_METRIC,
                "decision", evaluation.decision().name(),
                "risk_level", evaluation.riskLevel().name()).increment();
        return evaluation;
    }

    private String normalizeKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String key = rawKey.trim();
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new InvalidRequestException(
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be 1-128 characters using letters, digits, dot, underscore, colon or hyphen");
        }
        return key;
    }

    private Claim claim(String key, String fingerprint) {
        synchronized (slots) {
            Instant now = clock.instant();
            removeExpired(now);

            Slot existing = slots.get(key);
            if (existing != null) {
                if (!existing.fingerprint().equals(fingerprint)) {
                    throw new ConflictException(
                            "IDEMPOTENCY_KEY_CONFLICT",
                            "Idempotency-Key has already been used with a different request");
                }
                return new Claim(existing, false);
            }

            makeCapacity();
            Slot created = new Slot(fingerprint, now);
            slots.put(key, created);
            return new Claim(created, true);
        }
    }

    private void removeExpired(Instant now) {
        slots.entrySet().removeIf(entry -> entry.getValue().isExpired(now, properties.retention()));
    }

    private void makeCapacity() {
        if (slots.size() < properties.maxEntries()) {
            return;
        }

        List<Map.Entry<String, Slot>> completed = new ArrayList<>(slots.entrySet()).stream()
                .filter(entry -> entry.getValue().isCompleted())
                .sorted(Comparator.comparing(entry -> entry.getValue().completedAt()))
                .toList();
        for (Map.Entry<String, Slot> entry : completed) {
            slots.remove(entry.getKey(), entry.getValue());
            if (slots.size() < properties.maxEntries()) {
                return;
            }
        }

        throw new ServiceCapacityException(
                "IDEMPOTENCY_CAPACITY_EXCEEDED",
                "Too many idempotent evaluations are currently in progress; retry later");
    }

    private void complete(Slot slot, UnderwritingEvaluation evaluation) {
        synchronized (slots) {
            slot.complete(evaluation, clock.instant());
        }
    }

    private void fail(String key, Slot slot, Throwable error) {
        synchronized (slots) {
            slot.fail(error, clock.instant());
            slots.remove(key, slot);
        }
    }

    private UnderwritingEvaluation await(Slot slot) {
        try {
            return slot.future().join();
        }
        catch (CompletionException error) {
            if (error.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (error.getCause() instanceof Error fatal) {
                throw fatal;
            }
            throw new IllegalStateException("Idempotent evaluation failed", error.getCause());
        }
    }

    private String fingerprint(EvaluationRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, request.sessionId());
            update(digest, request.policyNo());
            update(digest, request.question());
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private enum SubmissionOutcome {
        CREATED,
        REPLAYED,
        CONFLICT,
        FAILED
    }

    private record Claim(Slot slot, boolean owner) {
    }

    private static final class Slot {

        private final String fingerprint;
        private final Instant createdAt;
        private final CompletableFuture<UnderwritingEvaluation> future = new CompletableFuture<>();
        private volatile Instant completedAt;

        private Slot(String fingerprint, Instant createdAt) {
            this.fingerprint = fingerprint;
            this.createdAt = createdAt;
        }

        private String fingerprint() {
            return fingerprint;
        }

        private CompletableFuture<UnderwritingEvaluation> future() {
            return future;
        }

        private boolean isCompleted() {
            return completedAt != null;
        }

        private Instant completedAt() {
            return completedAt == null ? createdAt : completedAt;
        }

        private boolean isExpired(Instant now, java.time.Duration retention) {
            Instant completed = completedAt;
            return completed != null && !completed.plus(retention).isAfter(now);
        }

        private void complete(UnderwritingEvaluation evaluation, Instant completedAt) {
            this.completedAt = completedAt;
            future.complete(evaluation);
        }

        private void fail(Throwable error, Instant completedAt) {
            this.completedAt = completedAt;
            future.completeExceptionally(error);
        }
    }
}
