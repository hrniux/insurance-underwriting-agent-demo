package com.hrniux.underwriting.model;

@FunctionalInterface
public interface ModelGateway {

    ModelResponse generate(ModelRequest request);
}
