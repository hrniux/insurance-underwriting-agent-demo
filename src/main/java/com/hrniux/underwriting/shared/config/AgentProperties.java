package com.hrniux.underwriting.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agent")
public record AgentProperties(Model model, Rag rag) {

    public AgentProperties {
        model = model == null ? new Model(null, null, null, null, null, null, 0, null, false) : model;
        rag = rag == null ? new Rag(0, 0, 0, 0) : rag;
    }

    public record Model(
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            Duration connectTimeout,
            Duration readTimeout,
            int maxAttempts,
            Duration retryBackoff,
            boolean fallbackToMock) {

        public Model {
            provider = valueOrDefault(provider, "mock");
            baseUrl = valueOrDefault(baseUrl, "http://localhost:11434");
            apiKey = valueOrDefault(apiKey, "demo-key");
            model = valueOrDefault(model, "deterministic-underwriter-v1");
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
            maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
            retryBackoff = retryBackoff == null ? Duration.ofMillis(100) : retryBackoff;
        }

        @Override
        public String toString() {
            return "Model[provider=%s, baseUrl=%s, apiKey=REDACTED, model=%s, connectTimeout=%s, "
                    + "readTimeout=%s, maxAttempts=%d, retryBackoff=%s, fallbackToMock=%s]"
                    .formatted(provider, baseUrl, model, connectTimeout, readTimeout, maxAttempts, retryBackoff,
                            fallbackToMock);
        }
    }

    public record Rag(int embeddingDimensions, int chunkSize, int chunkOverlap, int topK) {

        public Rag {
            embeddingDimensions = embeddingDimensions <= 0 ? 256 : embeddingDimensions;
            chunkSize = chunkSize <= 0 ? 500 : chunkSize;
            chunkOverlap = chunkOverlap < 0 ? 80 : chunkOverlap;
            topK = topK <= 0 ? 4 : topK;
        }
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
