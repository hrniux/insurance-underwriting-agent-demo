package com.hrniux.underwriting.shared.error;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String errorCode, String resourceId) {
        super(errorCode, "Resource not found: " + resourceId);
    }
}
