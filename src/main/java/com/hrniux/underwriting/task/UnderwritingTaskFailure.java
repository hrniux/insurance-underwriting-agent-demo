package com.hrniux.underwriting.task;

public record UnderwritingTaskFailure(String errorCode, String message) {

    public UnderwritingTaskFailure {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
