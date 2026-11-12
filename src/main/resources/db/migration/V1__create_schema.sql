-- Terminal registry. Lookup key is the business identifier, which keeps the lock target stable.
CREATE TABLE terminal (
    terminal_id VARCHAR(32)  PRIMARY KEY,
    status      VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_terminal_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
);

-- Append-only assessment audit. Holds no card data.
CREATE TABLE fraud_assessment (
    id                    UUID          PRIMARY KEY,
    terminal_id           VARCHAR(32)   NOT NULL,
    amount                DECIMAL(19,4) NOT NULL,
    currency              VARCHAR(3)    NOT NULL,
    terminal_threat_score INTEGER       NOT NULL,
    rejected              BOOLEAN       NOT NULL,
    fraud_score           INTEGER       NOT NULL,
    assessed_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_assessment_threat CHECK (terminal_threat_score BETWEEN 0 AND 100),
    CONSTRAINT ck_assessment_score  CHECK (fraud_score BETWEEN 0 AND 100)
);

-- Serves the velocity query: count by terminal within a time range.
CREATE INDEX ix_fraud_assessment_terminal_time ON fraud_assessment (terminal_id, assessed_at);
