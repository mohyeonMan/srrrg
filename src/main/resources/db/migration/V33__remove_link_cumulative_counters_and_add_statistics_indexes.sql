ALTER TABLE links
    DROP COLUMN access_count,
    DROP COLUMN redirect_count;

CREATE INDEX ix_links_active_campaign_id
    ON links (campaign_id, id)
    WHERE NOT is_deleted;

CREATE INDEX ix_links_active_project_id
    ON links (project_id, id)
    WHERE NOT is_deleted;

DROP INDEX idx_link_access_events_link_time;

CREATE INDEX idx_link_access_events_link_time
    ON link_access_events (link_id, accessed_at DESC)
    INCLUDE (outcome, is_bot);
