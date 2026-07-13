package com.hrniux.underwriting.session;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemorySessionRepository implements SessionRepository {

    private final ConcurrentHashMap<String, UnderwritingSession> sessions = new ConcurrentHashMap<>();

    @Override
    public UnderwritingSession save(UnderwritingSession session) {
        sessions.put(session.id(), session);
        return session;
    }

    @Override
    public Optional<UnderwritingSession> findById(String id) {
        return Optional.ofNullable(sessions.get(id));
    }
}
