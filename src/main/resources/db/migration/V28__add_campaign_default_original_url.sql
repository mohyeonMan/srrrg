ALTER TABLE campaigns
    ADD COLUMN default_original_url VARCHAR(2048);

ALTER TABLE links
    ALTER COLUMN original_url DROP NOT NULL,
    ADD CONSTRAINT ck_links_campaign_destination
        CHECK (original_url IS NOT NULL OR campaign_id IS NOT NULL);

ALTER TABLE campaign_import_rows
    ALTER COLUMN original_url DROP NOT NULL;
