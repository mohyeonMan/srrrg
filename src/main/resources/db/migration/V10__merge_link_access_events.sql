ALTER TABLE links
    RENAME COLUMN click_count TO access_count;

CREATE TABLE link_access_events (
    id BIGSERIAL PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links(id) ON DELETE CASCADE,

    accessed_at TIMESTAMPTZ NOT NULL,
    outcome VARCHAR(20) NOT NULL,

    ip_address VARCHAR(45),
    referer TEXT,
    user_agent TEXT,

    browser_name VARCHAR(50),
    browser_version VARCHAR(50),
    os_name VARCHAR(50),
    os_version VARCHAR(50),
    device_type VARCHAR(30),

    is_bot BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_link_access_events_outcome
        CHECK (outcome IN ('REDIRECTED', 'BLOCKED', 'CHECK_FAILED', 'URL_CHANGED'))
);

CREATE INDEX idx_link_access_events_link_time
    ON link_access_events (link_id, accessed_at DESC);

DROP TABLE link_click_events;
DROP TABLE link_redirect_events;
