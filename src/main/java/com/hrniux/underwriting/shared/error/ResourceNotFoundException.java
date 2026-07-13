package com.hrniux.underwriting.shared.error;

public class ResourceNotFoundException extends RuntimeException {

    private final String errorCode;

    public ResourceNotFoundException(String errorCode, String resourceId) {
        super("Resource not found: " + resourceId);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
