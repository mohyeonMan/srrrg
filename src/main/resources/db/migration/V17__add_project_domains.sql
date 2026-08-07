CREATE TABLE project_domains (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE REFERENCES projects(id) ON DELETE RESTRICT,
    hostname VARCHAR(253) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_domains_id_project UNIQUE (id, project_id),
    CONSTRAINT ck_project_domains_hostname_lower CHECK (hostname = LOWER(hostname))
);

WITH platform AS (
    SELECT LOWER(SUBSTRING('${platformBaseUrl}' FROM '^[A-Za-z][A-Za-z0-9+.-]*://([^/:]+)')) AS hostname
)
INSERT INTO project_domains (project_id, hostname, created_at)
SELECT projects.id, projects.slug || '.' || platform.hostname, projects.created_at
FROM projects
CROSS JOIN platform;

ALTER TABLE links
    ADD COLUMN domain_id BIGINT;

UPDATE links
SET domain_id = project_domains.id
FROM project_domains
WHERE links.project_id = project_domains.project_id;

ALTER TABLE links
    DROP CONSTRAINT links_code_key,
    ADD CONSTRAINT fk_links_domain_project
        FOREIGN KEY (domain_id, project_id)
        REFERENCES project_domains(id, project_id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT ck_links_project_domain
        CHECK ((project_id IS NULL) = (domain_id IS NULL));

CREATE UNIQUE INDEX uq_links_anonymous_code
    ON links(code)
    WHERE project_id IS NULL;

CREATE UNIQUE INDEX uq_links_domain_code
    ON links(domain_id, code)
    WHERE domain_id IS NOT NULL;
