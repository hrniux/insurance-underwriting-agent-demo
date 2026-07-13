package com.hrniux.underwriting.shared.error;

public class PromptRenderException extends RuntimeException {

    private final String errorCode;

    public PromptRenderException(String detail) {
        super(detail);
        this.errorCode = "PROMPT_RENDER_FAILED";
    }

    public String errorCode() {
        return errorCode;
    }
}
