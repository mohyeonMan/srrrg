CREATE TABLE campaign_import_row_utm_values (
    import_row_id BIGINT NOT NULL REFERENCES campaign_import_rows(id) ON DELETE CASCADE,
    utm_template_id BIGINT NOT NULL,
    utm_template_field_id BIGINT NOT NULL,
    value VARCHAR(500) NOT NULL,
    PRIMARY KEY (import_row_id, utm_template_field_id),
    CONSTRAINT fk_campaign_import_row_utm_values_field
        FOREIGN KEY (utm_template_field_id, utm_template_id)
        REFERENCES utm_template_fields (id, utm_template_id)
);
