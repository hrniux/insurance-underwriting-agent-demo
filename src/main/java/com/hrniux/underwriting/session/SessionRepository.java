package com.hrniux.underwriting.session;

import java.util.Optional;

public interface SessionRepository {

    UnderwritingSession save(UnderwritingSession session);

    Optional<UnderwritingSession> findById(String id);
}
