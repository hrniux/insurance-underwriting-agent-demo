package com.hrniux.underwriting.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agent.idempotency")
public record IdempotencyProperties(int maxEntries, Duration retention) {

    public IdempotencyProperties {
        maxEntries = maxEntries <= 0 ? 1_000 : maxEntries;
        retention = retention == null ? Duration.ofHours(24) : retention;
        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("idempotency retention must be positive");
        }
    }
}
