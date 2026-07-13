package com.hrniux.underwriting.model;

public class ModelUnavailableException extends RuntimeException {

    private final String errorCode;
    private final int attempts;

    public ModelUnavailableException(String errorCode, String message, int attempts, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.attempts = attempts;
    }

    public String errorCode() {
        return errorCode;
    }

    public int attempts() {
        return attempts;
    }
}
