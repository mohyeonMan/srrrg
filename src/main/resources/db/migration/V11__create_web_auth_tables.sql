CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(320),
    email_verified_at TIMESTAMPTZ,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_users_verified_email
    ON users (email)
    WHERE email IS NOT NULL AND email_verified_at IS NOT NULL;

CREATE TABLE oauth_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(320),
    provider_email_verified BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_oauth_accounts_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT ck_oauth_accounts_provider CHECK (provider IN ('GOOGLE', 'KAKAO', 'GITHUB'))
);

CREATE INDEX ix_oauth_accounts_user_id ON oauth_accounts (user_id);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_family_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    replaced_by_token_id BIGINT REFERENCES refresh_tokens (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens (token_family_id);

CREATE TABLE oauth_authorization_requests (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    state VARCHAR(255) NOT NULL UNIQUE,
    registration_id VARCHAR(20) NOT NULL,
    authorization_uri VARCHAR(1000) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    redirect_uri VARCHAR(1000) NOT NULL,
    scopes VARCHAR(1000) NOT NULL,
    code_verifier VARCHAR(255) NOT NULL,
    code_challenge VARCHAR(255) NOT NULL,
    return_path VARCHAR(1000) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_oauth_authorization_requests_expires_at
    ON oauth_authorization_requests (expires_at);

CREATE TABLE oauth_account_link_requests (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    existing_user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(320) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    return_path VARCHAR(1000) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_oauth_account_link_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT ck_oauth_account_link_provider CHECK (provider IN ('GOOGLE', 'KAKAO', 'GITHUB'))
);

CREATE INDEX ix_oauth_account_link_requests_expires_at
    ON oauth_account_link_requests (expires_at);
