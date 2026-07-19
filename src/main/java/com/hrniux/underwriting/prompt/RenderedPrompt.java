package com.hrniux.underwriting.prompt;

import java.util.Objects;

public record RenderedPrompt(
        PromptSnapshot snapshot,
        String content) {

    public RenderedPrompt {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }
}
