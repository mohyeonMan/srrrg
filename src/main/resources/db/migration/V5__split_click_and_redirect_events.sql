ALTER TABLE links
ADD COLUMN redirect_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE link_click_events (
    id BIGSERIAL PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links(id) ON DELETE CASCADE,

    clicked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    ip_address VARCHAR(45),
    referer TEXT,
    user_agent TEXT,

    browser_name VARCHAR(50),
    browser_version VARCHAR(50),
    os_name VARCHAR(50),
    os_version VARCHAR(50),
    device_type VARCHAR(30),

    is_bot BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_link_click_events_link_time
    ON link_click_events (link_id, clicked_at DESC);

CREATE TABLE link_redirect_events (
    id BIGSERIAL PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links(id) ON DELETE CASCADE,

    redirected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    ip_address VARCHAR(45),
    referer TEXT,
    user_agent TEXT,

    browser_name VARCHAR(50),
    browser_version VARCHAR(50),
    os_name VARCHAR(50),
    os_version VARCHAR(50),
    device_type VARCHAR(30),

    is_bot BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_link_redirect_events_link_time
    ON link_redirect_events (link_id, redirected_at DESC);
