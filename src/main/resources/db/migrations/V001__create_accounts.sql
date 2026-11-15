-- Accounts and Minecraft identity bindings for plugin authentication.

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    username        TEXT NOT NULL,
    email           TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX accounts_username_unique ON accounts (username);
CREATE UNIQUE INDEX accounts_email_unique ON accounts (email);

CREATE TABLE account_minecraft_identities (
    id              UUID PRIMARY KEY,
    account_id      UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    minecraft_uuid  UUID NOT NULL,
    minecraft_name  TEXT NULL,
    bound_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at    TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX account_minecraft_identities_uuid_unique
    ON account_minecraft_identities (minecraft_uuid);

CREATE INDEX account_minecraft_identities_account_id_idx
    ON account_minecraft_identities (account_id);
