package com.hrniux.underwriting.session;

import java.time.Instant;
import java.util.Objects;

public record SessionMessage(SessionRole role, String content, Instant createdAt) {

    public SessionMessage {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        content = content.trim();
    }
}
