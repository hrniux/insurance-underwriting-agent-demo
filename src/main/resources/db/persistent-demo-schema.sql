CREATE TABLE IF NOT EXISTS underwriting_session (
    id VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload CHARACTER LARGE OBJECT NOT NULL
);

CREATE TABLE IF NOT EXISTS underwriting_evaluation (
    id VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload CHARACTER LARGE OBJECT NOT NULL
);

CREATE TABLE IF NOT EXISTS underwriting_human_review (
    evaluation_id VARCHAR(64) PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload CHARACTER LARGE OBJECT NOT NULL,
    CONSTRAINT fk_review_evaluation
        FOREIGN KEY (evaluation_id) REFERENCES underwriting_evaluation (id)
);

CREATE INDEX IF NOT EXISTS idx_evaluation_created_at
    ON underwriting_evaluation (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_reviewed_at
    ON underwriting_human_review (reviewed_at DESC);
