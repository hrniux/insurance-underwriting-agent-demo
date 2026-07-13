package com.hrniux.underwriting.agent;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.hrniux.underwriting.model.ModelResponse;
import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.rule.RiskLevel;
import com.hrniux.underwriting.rule.RuleResult;
import com.hrniux.underwriting.tool.ToolCallTrace;

public record UnderwritingEvaluation(
        String id,
        String sessionId,
        String policyNo,
        String question,
        Decision decision,
        RiskLevel riskLevel,
        int riskScore,
        String summary,
        List<String> reasons,
        List<String> recommendedActions,
        List<Evidence> evidence,
        List<RuleResult> ruleHits,
        List<ToolCallTrace> toolTraces,
        List<StepTrace> stepTraces,
        ModelResponse modelResponse,
        Instant createdAt) {

    public UnderwritingEvaluation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(policyNo, "policyNo must not be null");
        Objects.requireNonNull(question, "question must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        reasons = List.copyOf(reasons);
        recommendedActions = List.copyOf(recommendedActions);
        evidence = List.copyOf(evidence);
        ruleHits = List.copyOf(ruleHits);
        toolTraces = List.copyOf(toolTraces);
        stepTraces = List.copyOf(stepTraces);
        Objects.requireNonNull(modelResponse, "modelResponse must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public UnderwritingEvaluation withStepTraces(List<StepTrace> traces) {
        return new UnderwritingEvaluation(id, sessionId, policyNo, question, decision, riskLevel, riskScore, summary,
                reasons, recommendedActions, evidence, ruleHits, toolTraces, traces, modelResponse, createdAt);
    }
}
