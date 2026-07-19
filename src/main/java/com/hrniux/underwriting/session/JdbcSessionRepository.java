package com.hrniux.underwriting.session;

import java.sql.Timestamp;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hrniux.underwriting.shared.persistence.PersistenceJsonCodec;

@Repository
@Profile("persistent-demo")
public class JdbcSessionRepository implements SessionRepository {

    private static final String UPSERT = """
            MERGE INTO underwriting_session (id, created_at, updated_at, payload)
            KEY (id) VALUES (?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final PersistenceJsonCodec codec;

    public JdbcSessionRepository(JdbcTemplate jdbc, PersistenceJsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public UnderwritingSession save(UnderwritingSession session) {
        jdbc.update(
                UPSERT,
                session.id(),
                Timestamp.from(session.createdAt()),
                Timestamp.from(session.updatedAt()),
                codec.write(session));
        return session;
    }

    @Override
    public Optional<UnderwritingSession> findById(String id) {
        return jdbc.query(
                "SELECT payload FROM underwriting_session WHERE id = ?",
                (result, row) -> codec.read(result.getString("payload"), UnderwritingSession.class),
                id).stream().findFirst();
    }
}
