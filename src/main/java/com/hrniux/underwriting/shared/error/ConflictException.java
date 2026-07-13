package com.hrniux.underwriting.shared.error;

public class ConflictException extends DomainException {

    public ConflictException(String errorCode, String detail) {
        super(errorCode, detail);
    }
}
