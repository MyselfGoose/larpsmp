-- First-class competitor / spectator participation and account-keyed server operators.

CREATE TABLE account_participation (
    account_id  UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    competitor  BOOLEAN NOT NULL DEFAULT FALSE,
    spectator   BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT account_participation_not_both CHECK (NOT (competitor AND spectator))
);

CREATE INDEX account_participation_competitor_idx
    ON account_participation (competitor)
    WHERE competitor = TRUE;

CREATE TABLE account_server_operators (
    account_id  UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by  TEXT NOT NULL DEFAULT ''
);

-- Existing rostered or already-funded accounts become competitors.
INSERT INTO account_participation (account_id, competitor, spectator, updated_at)
SELECT DISTINCT a.id, TRUE, FALSE, NOW()
FROM accounts a
WHERE EXISTS (
        SELECT 1 FROM team_memberships m WHERE m.account_id = a.id
    )
   OR EXISTS (
        SELECT 1 FROM wallets w
        WHERE w.account_id = a.id AND w.starting_balance_granted = TRUE
    )
ON CONFLICT (account_id) DO NOTHING;

-- Preserve vanilla op for anyone who already held plugin admin before the split.
INSERT INTO account_server_operators (account_id, granted_at, granted_by)
SELECT account_id, granted_at, COALESCE(NULLIF(granted_by, ''), 'V015-backfill')
FROM account_plugin_admins
ON CONFLICT (account_id) DO NOTHING;
