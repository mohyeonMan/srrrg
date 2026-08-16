ALTER TABLE users DROP COLUMN email_verified_at;

CREATE UNIQUE INDEX uq_users_verified_email
    ON users (email)
    WHERE email IS NOT NULL;
