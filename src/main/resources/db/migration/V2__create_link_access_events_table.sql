CREATE TABLE link_access_events (
    id BIGSERIAL PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links(id) ON DELETE CASCADE,

    accessed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    ip_address VARCHAR(45),
    country_code VARCHAR(2),
    region VARCHAR(100),
    city VARCHAR(100),

    referer TEXT,
    user_agent TEXT,

    browser_name VARCHAR(50),
    browser_version VARCHAR(50),
    os_name VARCHAR(50),
    os_version VARCHAR(50),
    device_type VARCHAR(30),

    is_bot BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_link_access_events_link_time
    ON link_access_events (link_id, accessed_at DESC);
