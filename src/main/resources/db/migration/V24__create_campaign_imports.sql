CREATE TABLE campaign_imports (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
    utm_template_id BIGINT,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    idempotency_request_hash VARCHAR(64) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    processed_rows INT NOT NULL DEFAULT 0,
    succeeded_rows INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(100),
    lease_expires_at TIMESTAMPTZ,
    attempt_count INT NOT NULL DEFAULT 0,
    created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_by_api_key_id BIGINT REFERENCES project_api_keys(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_campaign_imports_campaign_project
        FOREIGN KEY (campaign_id, project_id)
        REFERENCES campaigns (id, project_id),
    CONSTRAINT ck_campaign_imports_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX ix_campaign_imports_campaign_id ON campaign_imports(campaign_id);

CREATE UNIQUE INDEX uq_campaign_imports_campaign_idempotency
    ON campaign_imports(campaign_id, idempotency_key);

CREATE UNIQUE INDEX uq_campaign_imports_active_project
    ON campaign_imports(project_id)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX ix_campaign_imports_lease
    ON campaign_imports(status, lease_expires_at)
    WHERE status IN ('PENDING', 'PROCESSING');
