package com.hrniux.underwriting.model;

public class RoutingModelGateway implements ModelGateway {

    private final ModelGateway primary;
    private final DeterministicMockModelGateway fallback;
    private final boolean fallbackEnabled;

    public RoutingModelGateway(
            ModelGateway primary,
            DeterministicMockModelGateway fallback,
            boolean fallbackEnabled) {
        this.primary = primary;
        this.fallback = fallback;
        this.fallbackEnabled = fallbackEnabled;
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        try {
            return primary.generate(request);
        }
        catch (ModelUnavailableException error) {
            if (!fallbackEnabled || primary == fallback) {
                throw error;
            }
            return fallback.generate(request).asFallback();
        }
    }
}
