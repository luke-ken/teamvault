-- Schema per ADR-003: shared schema, company_id on every tenant-owned table.

CREATE TABLE company (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       text        NOT NULL,
    tier       text        NOT NULL DEFAULT 'SMALL',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_company_tier CHECK (tier IN ('SMALL', 'LARGE'))
);

CREATE TABLE app_user (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    email         text        NOT NULL,
    password_hash text        NOT NULL,
    display_name  text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE TABLE membership (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES app_user (id),
    company_id uuid        NOT NULL REFERENCES company (id),
    role       text        NOT NULL DEFAULT 'MEMBER',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_membership_company_user UNIQUE (company_id, user_id),
    CONSTRAINT ck_membership_role CHECK (role IN ('MEMBER', 'ADMIN'))
);

-- The unique above serves "members of a company"; this serves the reverse
-- path "companies of a user", hit on every authenticated request.
CREATE INDEX idx_membership_user ON membership (user_id);

CREATE TABLE file_metadata (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   uuid        NOT NULL REFERENCES company (id),
    uploaded_by  uuid        NOT NULL REFERENCES app_user (id),
    filename     text        NOT NULL,
    content_type text        NOT NULL,
    size_bytes   bigint      NOT NULL,
    storage_key  text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_file_company_filename UNIQUE (company_id, filename),
    CONSTRAINT uq_file_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_file_company_created ON file_metadata (company_id, created_at);
