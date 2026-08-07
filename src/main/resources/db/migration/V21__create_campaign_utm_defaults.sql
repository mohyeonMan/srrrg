CREATE TABLE campaign_utm_defaults (
    campaign_id BIGINT NOT NULL,
    utm_template_id BIGINT NOT NULL,
    utm_template_field_id BIGINT NOT NULL,
    default_value VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (campaign_id, utm_template_field_id),
    CONSTRAINT fk_campaign_utm_defaults_campaign
        FOREIGN KEY (campaign_id, utm_template_id)
        REFERENCES campaigns (id, utm_template_id),
    CONSTRAINT fk_campaign_utm_defaults_field
        FOREIGN KEY (utm_template_field_id, utm_template_id)
        REFERENCES utm_template_fields (id, utm_template_id)
);
