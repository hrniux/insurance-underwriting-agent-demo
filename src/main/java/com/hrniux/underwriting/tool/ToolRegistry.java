package com.hrniux.underwriting.tool;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hrniux.underwriting.shared.error.DomainException;

@Service
public class ToolRegistry {

    private static final int DEFAULT_TRACE_LIMIT = 1_000;

    private final UnderwritingFactTools factTools;
    private final Clock clock;
    private final int traceLimit;
    private final Deque<ToolCallTrace> traces = new ArrayDeque<>();

    @Autowired
    public ToolRegistry(UnderwritingFactTools factTools) {
        this(factTools, Clock.systemUTC(), DEFAULT_TRACE_LIMIT);
    }

    ToolRegistry(UnderwritingFactTools factTools, Clock clock) {
        this(factTools, clock, DEFAULT_TRACE_LIMIT);
    }

    ToolRegistry(UnderwritingFactTools factTools, Clock clock, int traceLimit) {
        this.factTools = factTools;
        this.clock = clock;
        if (traceLimit <= 0) {
            throw new IllegalArgumentException("traceLimit must be positive");
        }
        this.traceLimit = traceLimit;
    }

    public ToolInvocation<?> invoke(ToolName toolName, String policyNo) {
        return tryInvoke(toolName, policyNo).requiredInvocation();
    }

    public ToolAttempt<?> tryInvoke(ToolName toolName, String policyNo) {
        if (toolName == ToolName.VALIDATE_RULES) {
            throw new IllegalArgumentException("Use invokeRuleValidation for the rule engine");
        }
        return recordedAttempt(toolName, policyNo, () -> switch (toolName) {
            case GET_POLICY -> factTools.getPolicy(policyNo);
            case GET_QUOTATION -> factTools.getQuotation(policyNo);
            case GET_UNDERWRITING_HISTORY -> factTools.getUnderwritingHistory(policyNo);
            case GET_SURVEY_REPORT -> factTools.getSurveyReport(policyNo);
            case GET_DISASTER_RISK -> factTools.getDisasterRisk(policyNo);
            case VALIDATE_RULES -> throw new IllegalStateException("unreachable");
        });
    }

    public <T> ToolInvocation<T> invokeRuleValidation(String policyNo, Supplier<T> validation) {
        return recordedAttempt(ToolName.VALIDATE_RULES, policyNo, validation).requiredInvocation();
    }

    private <T> ToolAttempt<T> recordedAttempt(ToolName toolName, String policyNo, Supplier<T> invocation) {
        Instant startedAt = clock.instant();
        long startedNanos = System.nanoTime();
        String inputSummary = "policyNo=" + policyNo;
        try {
            T result = Objects.requireNonNull(invocation.get(), "tool result must not be null");
            ToolCallTrace trace = new ToolCallTrace(toolName, startedAt, elapsedMillis(startedNanos),
                    ToolCallStatus.SUCCESS, inputSummary, result.toString(), null);
            recordTrace(trace);
            return new ToolAttempt<>(result, trace, null);
        }
        catch (RuntimeException error) {
            String errorCode = error instanceof DomainException domain
                    ? domain.errorCode()
                    : "TOOL_CALL_FAILED";
            ToolCallTrace trace = new ToolCallTrace(
                    toolName,
                    startedAt,
                    elapsedMillis(startedNanos),
                    ToolCallStatus.FAILED,
                    inputSummary,
                    "tool call failed",
                    errorCode);
            recordTrace(trace);
            return new ToolAttempt<>(null, trace, error);
        }
    }

    public List<ToolName> toolNames() {
        return List.of(ToolName.values());
    }

    public List<ToolCallTrace> traces() {
        synchronized (traces) {
            return List.copyOf(traces);
        }
    }

    private void recordTrace(ToolCallTrace trace) {
        synchronized (traces) {
            if (traces.size() == traceLimit) {
                traces.removeFirst();
            }
            traces.addLast(trace);
        }
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }
}
