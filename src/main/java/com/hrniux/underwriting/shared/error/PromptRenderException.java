package com.hrniux.underwriting.shared.error;

public class PromptRenderException extends DomainException {

    public PromptRenderException(String detail) {
        super("PROMPT_RENDER_FAILED", detail);
    }
}
