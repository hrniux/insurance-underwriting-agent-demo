package com.hrniux.underwriting.session;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record UnderwritingSession(
        String id,
        List<SessionMessage> messages,
        Instant createdAt,
        Instant updatedAt) {

    public UnderwritingSession {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public UnderwritingSession append(SessionMessage message, Instant now) {
        var updatedMessages = new java.util.ArrayList<>(messages);
        updatedMessages.add(Objects.requireNonNull(message, "message must not be null"));
        return new UnderwritingSession(id, updatedMessages, createdAt, now);
    }
}
