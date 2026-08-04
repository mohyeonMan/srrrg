CREATE TABLE project_api_keys (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    key_prefix VARCHAR(32) NOT NULL UNIQUE,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX ix_project_api_keys_project_id ON project_api_keys(project_id);

CREATE TABLE project_api_key_scopes (
    api_key_id BIGINT NOT NULL REFERENCES project_api_keys(id) ON DELETE CASCADE,
    scope VARCHAR(32) NOT NULL,
    PRIMARY KEY (api_key_id, scope),
    CONSTRAINT ck_project_api_key_scopes_scope CHECK (scope IN (
        'links:read', 'links:write', 'campaigns:read', 'campaigns:write', 'stats:read'
    ))
);
