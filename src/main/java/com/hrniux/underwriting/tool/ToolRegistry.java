package com.hrniux.underwriting.tool;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hrniux.underwriting.shared.error.ResourceNotFoundException;

@Service
public class ToolRegistry {

    private final UnderwritingFactTools factTools;
    private final Clock clock;
    private final CopyOnWriteArrayList<ToolCallTrace> traces = new CopyOnWriteArrayList<>();

    @Autowired
    public ToolRegistry(UnderwritingFactTools factTools) {
        this(factTools, Clock.systemUTC());
    }

    ToolRegistry(UnderwritingFactTools factTools, Clock clock) {
        this.factTools = factTools;
        this.clock = clock;
    }

    public ToolInvocation<?> invoke(ToolName toolName, String policyNo) {
        Instant startedAt = clock.instant();
        long startedNanos = System.nanoTime();
        String inputSummary = "policyNo=" + policyNo;
        try {
            Object result = switch (toolName) {
                case GET_POLICY -> factTools.getPolicy(policyNo);
                case GET_QUOTATION -> factTools.getQuotation(policyNo);
                case GET_UNDERWRITING_HISTORY -> factTools.getUnderwritingHistory(policyNo);
                case GET_SURVEY_REPORT -> factTools.getSurveyReport(policyNo);
                case GET_DISASTER_RISK -> factTools.getDisasterRisk(policyNo);
                case VALIDATE_RULES -> throw new UnsupportedOperationException("Rule validation is handled by the rule module");
            };
            ToolCallTrace trace = new ToolCallTrace(toolName, startedAt, elapsedMillis(startedNanos),
                    ToolCallStatus.SUCCESS, inputSummary, result.toString(), null);
            traces.add(trace);
            return new ToolInvocation<>(result, trace);
        }
        catch (RuntimeException error) {
            String errorCode = error instanceof ResourceNotFoundException notFound
                    ? notFound.errorCode()
                    : "TOOL_CALL_FAILED";
            traces.add(new ToolCallTrace(toolName, startedAt, elapsedMillis(startedNanos), ToolCallStatus.FAILED,
                    inputSummary, "tool call failed", errorCode));
            throw error;
        }
    }

    public List<ToolName> toolNames() {
        return List.of(ToolName.values());
    }

    public List<ToolCallTrace> traces() {
        return List.copyOf(traces);
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }
}
