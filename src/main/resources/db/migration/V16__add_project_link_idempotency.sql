ALTER TABLE links
    ADD COLUMN idempotency_api_key_id BIGINT REFERENCES project_api_keys(id) ON DELETE SET NULL,
    ADD COLUMN idempotency_key VARCHAR(100),
    ADD COLUMN idempotency_request_hash VARCHAR(64),
    ADD CONSTRAINT ck_links_idempotency_fields CHECK (
        (idempotency_key IS NULL) = (idempotency_request_hash IS NULL)
    );

CREATE UNIQUE INDEX uq_links_api_key_idempotency
    ON links(idempotency_api_key_id, idempotency_key)
    WHERE idempotency_api_key_id IS NOT NULL AND idempotency_key IS NOT NULL;
