package com.hrniux.underwriting.shared.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("persistent-demo")
public class PersistenceJsonCodec {

    private final JsonMapper mapper;

    public PersistenceJsonCodec(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        }
        catch (JacksonException error) {
            throw new IllegalStateException(
                    "Could not serialize persisted " + value.getClass().getSimpleName(), error);
        }
    }

    public <T> T read(String payload, Class<T> type) {
        try {
            return mapper.readValue(payload, type);
        }
        catch (JacksonException error) {
            throw new IllegalStateException("Could not deserialize persisted " + type.getSimpleName(), error);
        }
    }
}
