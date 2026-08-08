ALTER TABLE links
    ADD COLUMN hostname VARCHAR(253);

UPDATE links
SET hostname = project_domains.hostname
FROM project_domains
WHERE links.domain_id = project_domains.id;

ALTER TABLE links
    ADD CONSTRAINT ck_links_project_hostname
        CHECK ((project_id IS NULL) = (hostname IS NULL)),
    ADD CONSTRAINT ck_links_hostname_lower
        CHECK (hostname = LOWER(hostname));

CREATE UNIQUE INDEX uq_links_hostname_code
    ON links(hostname, code)
    WHERE hostname IS NOT NULL;
