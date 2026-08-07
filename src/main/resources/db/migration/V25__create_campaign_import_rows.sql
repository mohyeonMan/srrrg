CREATE TABLE campaign_import_rows (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES campaign_imports(id) ON DELETE CASCADE,
    row_number INT NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    external_id VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    link_id BIGINT REFERENCES links(id) ON DELETE SET NULL,
    error_code VARCHAR(50),
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    CONSTRAINT uq_campaign_import_rows_import_row UNIQUE (import_id, row_number),
    CONSTRAINT ck_campaign_import_rows_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX ix_campaign_import_rows_import_id ON campaign_import_rows(import_id);

CREATE INDEX ix_campaign_import_rows_pending
    ON campaign_import_rows(import_id, row_number)
    WHERE status = 'PENDING';
