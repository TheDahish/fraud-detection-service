-- An Idempotency-Key makes a retry of the same request on the same terminal a refused duplicate, not a second attempt.
-- The fingerprint tells a retry (same payment) from a key reused for a different payment.
ALTER TABLE fraud_assessment ADD COLUMN idempotency_key VARCHAR(64);
ALTER TABLE fraud_assessment ADD COLUMN request_fingerprint VARCHAR(64);

-- Nulls are distinct in a unique index, so rows without a key never collide.
CREATE UNIQUE INDEX ux_fraud_assessment_idempotency ON fraud_assessment (terminal_id, idempotency_key);
