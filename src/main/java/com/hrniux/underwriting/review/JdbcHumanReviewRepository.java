package com.hrniux.underwriting.review;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hrniux.underwriting.shared.persistence.PersistenceJsonCodec;

@Repository
@Profile("persistent-demo")
public class JdbcHumanReviewRepository implements HumanReviewRepository {

    private static final String INSERT = """
            INSERT INTO underwriting_human_review (evaluation_id, id, reviewed_at, payload)
            VALUES (?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final PersistenceJsonCodec codec;

    public JdbcHumanReviewRepository(JdbcTemplate jdbc, PersistenceJsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public boolean create(HumanReview review) {
        try {
            jdbc.update(
                    INSERT,
                    review.evaluationId(),
                    review.id(),
                    Timestamp.from(review.reviewedAt()),
                    codec.write(review));
            return true;
        }
        catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public Optional<HumanReview> findByEvaluationId(String evaluationId) {
        return jdbc.query(
                "SELECT payload FROM underwriting_human_review WHERE evaluation_id = ?",
                (result, row) -> codec.read(result.getString("payload"), HumanReview.class),
                evaluationId).stream().findFirst();
    }

    @Override
    public List<HumanReview> findAll() {
        return jdbc.query(
                "SELECT payload FROM underwriting_human_review ORDER BY reviewed_at DESC",
                (result, row) -> codec.read(result.getString("payload"), HumanReview.class));
    }
}
