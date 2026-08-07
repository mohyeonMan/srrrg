CREATE TABLE campaigns (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
    utm_template_id BIGINT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMPTZ,
    CONSTRAINT uq_campaigns_id_project UNIQUE (id, project_id),
    CONSTRAINT uq_campaigns_id_utm_template UNIQUE (id, utm_template_id),
    CONSTRAINT fk_campaigns_utm_template_project
        FOREIGN KEY (utm_template_id, project_id)
        REFERENCES utm_templates (id, project_id)
);

CREATE INDEX ix_campaigns_project_id ON campaigns(project_id);
CREATE INDEX ix_campaigns_utm_template_id ON campaigns(utm_template_id);
