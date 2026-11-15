-- Email verification flags and one-time email challenge codes.

ALTER TABLE accounts
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE accounts
    ADD COLUMN email_verified_at TIMESTAMPTZ NULL;

CREATE TABLE auth_email_challenges (
    id              UUID PRIMARY KEY,
    account_id      UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    purpose         TEXT NOT NULL,
    code_hash       TEXT NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    attempt_count   INT NOT NULL DEFAULT 0,
    max_attempts    INT NOT NULL,
    consumed_at     TIMESTAMPTZ NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_sent_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT auth_email_challenges_purpose_check
        CHECK (purpose IN ('SIGNUP_VERIFY', 'PASSWORD_RESET', 'USERNAME_RECOVERY'))
);

CREATE INDEX auth_email_challenges_account_purpose_active_idx
    ON auth_email_challenges (account_id, purpose, created_at DESC)
    WHERE consumed_at IS NULL;
