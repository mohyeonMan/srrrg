ALTER TABLE links
    ADD COLUMN campaign_id BIGINT,
    ADD COLUMN utm_template_id BIGINT,
    ADD COLUMN external_id VARCHAR(100);

ALTER TABLE links
    ADD CONSTRAINT uq_links_id_utm_template UNIQUE (id, utm_template_id),
    ADD CONSTRAINT fk_links_campaign_project
        FOREIGN KEY (campaign_id, project_id)
        REFERENCES campaigns (id, project_id),
    ADD CONSTRAINT fk_links_utm_template_project
        FOREIGN KEY (utm_template_id, project_id)
        REFERENCES utm_templates (id, project_id),
    ADD CONSTRAINT ck_links_campaign_requires_project
        CHECK (campaign_id IS NULL OR project_id IS NOT NULL);

CREATE INDEX ix_links_campaign_id ON links(campaign_id);

CREATE UNIQUE INDEX uq_links_campaign_external_id
    ON links(campaign_id, external_id)
    WHERE campaign_id IS NOT NULL AND external_id IS NOT NULL;
