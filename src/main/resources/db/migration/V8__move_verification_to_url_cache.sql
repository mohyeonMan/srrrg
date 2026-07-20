CREATE TABLE url_verifications (
    url_hash VARCHAR(64) PRIMARY KEY,
    original_url VARCHAR(2048) NOT NULL,
    verdict VARCHAR(16) NOT NULL CHECK (verdict IN ('SAFE', 'THREAT')),
    verified_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > verified_at)
);

CREATE INDEX idx_url_verifications_expires_at
    ON url_verifications (expires_at);

ALTER TABLE links
DROP COLUMN status,
DROP COLUMN verified_at;
