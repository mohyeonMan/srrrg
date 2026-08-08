ALTER TABLE link_utm_values ADD COLUMN field_name VARCHAR(50);
UPDATE link_utm_values value
   SET field_name = field.name
  FROM utm_template_fields field
 WHERE field.id = value.utm_template_field_id;
ALTER TABLE link_utm_values
    DROP CONSTRAINT fk_link_utm_values_link,
    DROP CONSTRAINT fk_link_utm_values_field,
    DROP CONSTRAINT link_utm_values_pkey,
    ALTER COLUMN field_name SET NOT NULL,
    DROP COLUMN utm_template_field_id,
    DROP COLUMN utm_template_id,
    ADD PRIMARY KEY (link_id, field_name),
    ADD CONSTRAINT fk_link_utm_values_link FOREIGN KEY (link_id) REFERENCES links(id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_link_utm_values_field_name CHECK (field_name ~ '^[a-z][a-z0-9_]{1,49}$');

ALTER TABLE campaign_utm_defaults ADD COLUMN field_name VARCHAR(50);
UPDATE campaign_utm_defaults value
   SET field_name = field.name
  FROM utm_template_fields field
 WHERE field.id = value.utm_template_field_id;
ALTER TABLE campaign_utm_defaults
    DROP CONSTRAINT fk_campaign_utm_defaults_campaign,
    DROP CONSTRAINT fk_campaign_utm_defaults_field,
    DROP CONSTRAINT campaign_utm_defaults_pkey,
    ALTER COLUMN field_name SET NOT NULL,
    DROP COLUMN utm_template_field_id,
    DROP COLUMN utm_template_id,
    ADD PRIMARY KEY (campaign_id, field_name),
    ADD CONSTRAINT fk_campaign_utm_defaults_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_campaign_utm_defaults_field_name CHECK (field_name ~ '^[a-z][a-z0-9_]{1,49}$');

ALTER TABLE campaign_import_row_utm_values ADD COLUMN field_name VARCHAR(50);
UPDATE campaign_import_row_utm_values value
   SET field_name = field.name
  FROM utm_template_fields field
 WHERE field.id = value.utm_template_field_id;
ALTER TABLE campaign_import_row_utm_values
    DROP CONSTRAINT fk_campaign_import_row_utm_values_field,
    DROP CONSTRAINT campaign_import_row_utm_values_pkey,
    ALTER COLUMN field_name SET NOT NULL,
    DROP COLUMN utm_template_field_id,
    DROP COLUMN utm_template_id,
    ADD PRIMARY KEY (import_row_id, field_name),
    ADD CONSTRAINT ck_campaign_import_row_utm_values_field_name CHECK (field_name ~ '^[a-z][a-z0-9_]{1,49}$');

ALTER TABLE link_access_events
    ADD COLUMN effective_utm JSONB NOT NULL DEFAULT '{}'::JSONB,
    DROP CONSTRAINT chk_link_access_events_outcome,
    ADD CONSTRAINT chk_link_access_events_outcome
        CHECK (outcome IN ('REDIRECTED', 'BLOCKED', 'CHECK_FAILED', 'URL_CHANGED', 'EXPIRED'));
