package com.hrniux.underwriting.prompt;

import java.util.Objects;

public record PromptSnapshot(
        String code,
        int version,
        String templateSha256) {

    private static final String LEGACY_CODE = "legacy-unavailable";
    private static final String LEGACY_FINGERPRINT = "unavailable";

    public PromptSnapshot {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        code = code.trim();
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        Objects.requireNonNull(templateSha256, "templateSha256 must not be null");
        if (version > 0 && !templateSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("templateSha256 must be a lowercase SHA-256 value");
        }
    }

    public static PromptSnapshot legacy() {
        return new PromptSnapshot(LEGACY_CODE, 0, LEGACY_FINGERPRINT);
    }

    public boolean available() {
        return version > 0;
    }
}
