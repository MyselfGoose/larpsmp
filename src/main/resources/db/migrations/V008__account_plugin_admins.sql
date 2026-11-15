-- Plugin administrators keyed by LarpSMP account (not Minecraft client UUID).

CREATE TABLE account_plugin_admins (
    account_id  UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by  TEXT NOT NULL DEFAULT ''
);
