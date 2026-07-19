package com.hrniux.underwriting.agent;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hrniux.underwriting.shared.persistence.PersistenceJsonCodec;

@Repository
@Profile("persistent-demo")
public class JdbcEvaluationRepository implements EvaluationRepository {

    private static final String UPSERT = """
            MERGE INTO underwriting_evaluation (id, created_at, payload)
            KEY (id) VALUES (?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final PersistenceJsonCodec codec;

    public JdbcEvaluationRepository(JdbcTemplate jdbc, PersistenceJsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public UnderwritingEvaluation save(UnderwritingEvaluation evaluation) {
        jdbc.update(
                UPSERT,
                evaluation.id(),
                Timestamp.from(evaluation.createdAt()),
                codec.write(evaluation));
        return evaluation;
    }

    @Override
    public Optional<UnderwritingEvaluation> findById(String id) {
        return jdbc.query(
                "SELECT payload FROM underwriting_evaluation WHERE id = ?",
                (result, row) -> codec.read(result.getString("payload"), UnderwritingEvaluation.class),
                id).stream().findFirst();
    }

    @Override
    public List<UnderwritingEvaluation> findAll() {
        return jdbc.query(
                "SELECT payload FROM underwriting_evaluation ORDER BY created_at DESC",
                (result, row) -> codec.read(result.getString("payload"), UnderwritingEvaluation.class));
    }
}
