package com.hrniux.underwriting.model;

import java.util.List;
import java.util.Objects;

import com.hrniux.underwriting.prompt.PromptSnapshot;
import com.hrniux.underwriting.rule.RuleEvaluation;

public record ModelRequest(
        String renderedPrompt,
        RuleEvaluation ruleEvaluation,
        List<String> evidence,
        List<String> warnings,
        PromptSnapshot prompt) {

    public ModelRequest {
        Objects.requireNonNull(renderedPrompt, "renderedPrompt must not be null");
        Objects.requireNonNull(ruleEvaluation, "ruleEvaluation must not be null");
        evidence = List.copyOf(evidence);
        warnings = List.copyOf(warnings);
        prompt = prompt == null ? PromptSnapshot.legacy() : prompt;
    }

    public ModelRequest(
            String renderedPrompt,
            RuleEvaluation ruleEvaluation,
            List<String> evidence,
            List<String> warnings) {
        this(renderedPrompt, ruleEvaluation, evidence, warnings, PromptSnapshot.legacy());
    }
}
