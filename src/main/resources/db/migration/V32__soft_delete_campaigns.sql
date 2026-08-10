ALTER TABLE campaigns
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE campaigns
   SET is_deleted = TRUE
 WHERE archived_at IS NOT NULL;

ALTER TABLE campaigns
    DROP COLUMN archived_at;

CREATE INDEX ix_campaigns_active_project_id
    ON campaigns(project_id, id DESC)
    WHERE NOT is_deleted;
