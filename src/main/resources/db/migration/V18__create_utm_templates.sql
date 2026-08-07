CREATE TABLE utm_templates (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_utm_templates_id_project UNIQUE (id, project_id)
);

CREATE INDEX ix_utm_templates_project_id ON utm_templates(project_id);

CREATE UNIQUE INDEX uq_utm_templates_project_name
    ON utm_templates(project_id, name)
    WHERE deleted_at IS NULL;
