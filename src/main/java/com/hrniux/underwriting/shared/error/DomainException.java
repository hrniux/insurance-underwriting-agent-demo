package com.hrniux.underwriting.shared.error;

public abstract class DomainException extends RuntimeException {

    private final String errorCode;

    protected DomainException(String errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
