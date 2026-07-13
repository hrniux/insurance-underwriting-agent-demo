package com.hrniux.underwriting.shared.error;

public class ConflictException extends RuntimeException {

    private final String errorCode;

    public ConflictException(String errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
