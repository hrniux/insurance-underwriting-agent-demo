package com.hrniux.underwriting.shared.error;

public class ServiceCapacityException extends DomainException {

    public ServiceCapacityException(String errorCode, String detail) {
        super(errorCode, detail);
    }
}
