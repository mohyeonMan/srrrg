CREATE TABLE utm_template_fields (
    id BIGSERIAL PRIMARY KEY,
    utm_template_id BIGINT NOT NULL REFERENCES utm_templates(id) ON DELETE RESTRICT,
    name VARCHAR(50) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_utm_template_fields_id_template UNIQUE (id, utm_template_id),
    CONSTRAINT ck_utm_template_fields_name_format CHECK (name ~ '^[a-z][a-z0-9_]{1,49}$')
);

CREATE INDEX ix_utm_template_fields_template_id ON utm_template_fields(utm_template_id);

CREATE UNIQUE INDEX uq_utm_template_fields_template_name
    ON utm_template_fields(utm_template_id, name)
    WHERE deleted_at IS NULL;
