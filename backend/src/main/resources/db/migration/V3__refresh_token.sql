CREATE TABLE refresh_token (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid        NOT NULL REFERENCES app_user (id),
    family_id           uuid        NOT NULL,
    token_hash          text        NOT NULL,
    expires_at          timestamptz NOT NULL,
    family_expires_at   timestamptz NOT NULL,
    used_at             timestamptz,
    revoked_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

-- The unique above serves the lookup on every refresh. Revocation hits a whole family,
-- the nightly sweep deletes by expiry.
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
CREATE INDEX idx_refresh_token_expires ON refresh_token (expires_at);