-- Economy settlement: competitor starting-balance flag, settings, kill money,
-- kill prices, idempotent settlements, and parent/child ledger linkage.

ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS starting_balance_granted BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE wallets w
SET starting_balance_granted = TRUE
WHERE EXISTS (
    SELECT 1
    FROM wallet_transactions t
    WHERE t.source_account_id = w.account_id
      AND t.type = 'STARTING_BALANCE'
      AND t.status = 'SUCCESS'
);

CREATE TABLE economy_settings (
    id                      SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    kill_money_global       BOOLEAN NOT NULL DEFAULT TRUE,
    team_totals_visible     BOOLEAN NOT NULL DEFAULT TRUE,
    reward_rotation_index   INTEGER NOT NULL DEFAULT 0 CHECK (reward_rotation_index >= 0),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO economy_settings (id) VALUES (1)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE economy_kill_prices (
    tier        SMALLINT PRIMARY KEY CHECK (tier BETWEEN 1 AND 4),
    amount      BIGINT NOT NULL CHECK (amount >= 0),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO economy_kill_prices (tier, amount) VALUES
    (1, 40),
    (2, 30),
    (3, 20),
    (4, 15)
ON CONFLICT (tier) DO NOTHING;

CREATE TABLE economy_kill_money_player_overrides (
    account_id  UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    enabled     BOOLEAN NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE economy_kill_money_events (
    event_id    TEXT PRIMARY KEY,
    enabled     BOOLEAN NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE economy_kill_money_event_participants (
    event_id    TEXT NOT NULL REFERENCES economy_kill_money_events(event_id) ON DELETE CASCADE,
    account_id  UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, account_id)
);

CREATE TABLE economy_settlements (
    kind                    TEXT NOT NULL,
    external_id             TEXT NOT NULL,
    outcome                 TEXT NOT NULL DEFAULT 'APPLIED',
    parent_transaction_id   UUID NULL,
    charged_total           BIGINT NOT NULL DEFAULT 0,
    detail_json             TEXT NOT NULL DEFAULT '',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (kind, external_id)
);

CREATE TABLE economy_admin_audit (
    id              UUID PRIMARY KEY,
    action          TEXT NOT NULL,
    scope           TEXT NOT NULL DEFAULT '',
    old_value       TEXT NOT NULL DEFAULT '',
    new_value       TEXT NOT NULL DEFAULT '',
    reason          TEXT NOT NULL DEFAULT '',
    actor_username  TEXT NOT NULL DEFAULT '',
    sender_type     TEXT NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE wallet_transactions
    ADD COLUMN IF NOT EXISTS parent_transaction_id UUID NULL REFERENCES wallet_transactions(id) ON DELETE SET NULL;

ALTER TABLE wallet_transactions
    ADD COLUMN IF NOT EXISTS external_ref_kind TEXT NOT NULL DEFAULT '';

ALTER TABLE wallet_transactions
    ADD COLUMN IF NOT EXISTS external_ref_id TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS wallet_transactions_parent_idx
    ON wallet_transactions (parent_transaction_id);

CREATE INDEX IF NOT EXISTS wallet_transactions_external_ref_idx
    ON wallet_transactions (external_ref_kind, external_ref_id);
