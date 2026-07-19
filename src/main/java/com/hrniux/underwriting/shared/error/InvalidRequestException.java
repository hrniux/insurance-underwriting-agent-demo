package com.hrniux.underwriting.shared.error;

public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String errorCode, String detail) {
        super(errorCode, detail);
    }
}
