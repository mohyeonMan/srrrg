ALTER TABLE projects
    ADD COLUMN subdomain VARCHAR(63),
    ADD COLUMN subdomain_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE projects
    ADD CONSTRAINT ck_projects_subdomain_format
        CHECK (subdomain IS NULL OR subdomain ~ '^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$'),
    ADD CONSTRAINT ck_projects_subdomain_enabled
        CHECK (NOT subdomain_enabled OR subdomain IS NOT NULL);

CREATE UNIQUE INDEX uq_projects_subdomain
    ON projects(subdomain)
    WHERE subdomain IS NOT NULL;

ALTER TABLE links
    ADD COLUMN subdomain VARCHAR(63);

ALTER TABLE links
    DROP CONSTRAINT fk_links_domain_project,
    DROP CONSTRAINT ck_links_project_domain,
    DROP CONSTRAINT ck_links_project_hostname,
    DROP CONSTRAINT ck_links_hostname_lower,
    ADD CONSTRAINT ck_links_subdomain_format
        CHECK (subdomain IS NULL OR subdomain ~ '^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$');

DROP INDEX uq_links_anonymous_code;
DROP INDEX uq_links_domain_code;
DROP INDEX uq_links_hostname_code;

ALTER TABLE links
    DROP COLUMN domain_id,
    DROP COLUMN hostname;

DROP TABLE project_domains;

ALTER TABLE projects
    DROP CONSTRAINT ck_projects_slug_format,
    DROP COLUMN slug;

CREATE UNIQUE INDEX uq_links_base_code
    ON links(code)
    WHERE subdomain IS NULL;

CREATE UNIQUE INDEX uq_links_subdomain_code
    ON links(subdomain, code)
    WHERE subdomain IS NOT NULL;

CREATE UNIQUE INDEX uq_links_project_code
    ON links(project_id, code)
    WHERE project_id IS NOT NULL;
