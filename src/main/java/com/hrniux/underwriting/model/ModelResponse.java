package com.hrniux.underwriting.model;

import java.util.List;
import java.util.Objects;

public record ModelResponse(
        String summary,
        List<String> reasons,
        List<String> recommendedActions,
        String provider,
        String model,
        int attempts,
        boolean fallbackUsed) {

    public ModelResponse {
        Objects.requireNonNull(summary, "summary must not be null");
        reasons = List.copyOf(reasons);
        recommendedActions = List.copyOf(recommendedActions);
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(model, "model must not be null");
    }

    public ModelResponse asFallback() {
        return new ModelResponse(summary, reasons, recommendedActions, provider, model, attempts, true);
    }
}
