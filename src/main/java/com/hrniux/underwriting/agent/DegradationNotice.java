package com.hrniux.underwriting.agent;

import java.util.Objects;

import com.hrniux.underwriting.rule.Decision;
import com.hrniux.underwriting.tool.ToolName;

public record DegradationNotice(
        String code,
        ToolName toolName,
        String errorCode,
        String message,
        Decision decisionFloor) {

    public DegradationNotice {
        code = requireText(code, "code");
        Objects.requireNonNull(toolName, "toolName must not be null");
        errorCode = requireText(errorCode, "errorCode");
        message = requireText(message, "message");
        Objects.requireNonNull(decisionFloor, "decisionFloor must not be null");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
