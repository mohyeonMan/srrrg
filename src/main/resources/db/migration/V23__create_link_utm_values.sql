CREATE TABLE link_utm_values (
    link_id BIGINT NOT NULL,
    utm_template_id BIGINT NOT NULL,
    utm_template_field_id BIGINT NOT NULL,
    value VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (link_id, utm_template_field_id),
    CONSTRAINT fk_link_utm_values_link
        FOREIGN KEY (link_id, utm_template_id)
        REFERENCES links (id, utm_template_id),
    CONSTRAINT fk_link_utm_values_field
        FOREIGN KEY (utm_template_field_id, utm_template_id)
        REFERENCES utm_template_fields (id, utm_template_id)
);

CREATE INDEX ix_link_utm_values_field_id ON link_utm_values(utm_template_field_id);
