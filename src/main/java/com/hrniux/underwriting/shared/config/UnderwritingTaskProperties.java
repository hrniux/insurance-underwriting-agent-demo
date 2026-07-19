package com.hrniux.underwriting.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agent.tasks")
public record UnderwritingTaskProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity,
        int maxEntries,
        Duration retention) {

    public UnderwritingTaskProperties {
        corePoolSize = corePoolSize <= 0 ? 2 : corePoolSize;
        maxPoolSize = maxPoolSize <= 0 ? 4 : maxPoolSize;
        queueCapacity = queueCapacity <= 0 ? 100 : queueCapacity;
        maxEntries = maxEntries <= 0 ? 1_000 : maxEntries;
        retention = retention == null ? Duration.ofHours(24) : retention;
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("task max pool size must be greater than or equal to core pool size");
        }
        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("task retention must be positive");
        }
    }
}
