CREATE TABLE campaign_link_batches (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES campaigns(id) ON DELETE RESTRICT,
    api_key_id BIGINT NOT NULL REFERENCES project_api_keys(id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_campaign_link_batches_key UNIQUE (api_key_id, idempotency_key)
);

CREATE TABLE campaign_link_batch_items (
    batch_id BIGINT NOT NULL REFERENCES campaign_link_batches(id) ON DELETE CASCADE,
    item_index INT NOT NULL,
    link_id BIGINT NOT NULL REFERENCES links(id) ON DELETE RESTRICT,
    PRIMARY KEY (batch_id, item_index)
);

CREATE INDEX ix_campaign_link_batch_items_link_id ON campaign_link_batch_items(link_id);
