ALTER TABLE projects
    ADD COLUMN slug VARCHAR(63),
    ADD COLUMN created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN archived_at TIMESTAMPTZ;

UPDATE projects SET slug = 'p-' || id WHERE slug IS NULL;

ALTER TABLE projects
    ALTER COLUMN slug SET NOT NULL,
    ADD CONSTRAINT ck_projects_slug_format
        CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$');

CREATE UNIQUE INDEX uq_projects_slug ON projects(slug);

ALTER TABLE links
    ADD COLUMN created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX ix_links_created_by_user_id ON links(created_by_user_id);
