-- Account-linked wallets and money transaction ledger.

CREATE TABLE wallets (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    balance    BIGINT NOT NULL CHECK (balance >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE wallet_transactions (
    id                          UUID PRIMARY KEY,
    type                        TEXT NOT NULL,
    status                      TEXT NOT NULL,
    amount                      BIGINT NOT NULL,
    reason                      TEXT NOT NULL DEFAULT '',
    actor_account_id            UUID NULL REFERENCES accounts(id) ON DELETE SET NULL,
    source_account_id           UUID NULL REFERENCES accounts(id) ON DELETE SET NULL,
    destination_account_id      UUID NULL REFERENCES accounts(id) ON DELETE SET NULL,
    source_balance_before       BIGINT NULL,
    source_balance_after        BIGINT NULL,
    destination_balance_before  BIGINT NULL,
    destination_balance_after   BIGINT NULL,
    failure_reason              TEXT NOT NULL DEFAULT '',
    authorization_action        TEXT NOT NULL DEFAULT '',
    command                     TEXT NOT NULL DEFAULT '',
    actor_username              TEXT NOT NULL DEFAULT '',
    sender_type                 TEXT NOT NULL DEFAULT '',
    created_at                  TIMESTAMPTZ NOT NULL
);

CREATE INDEX wallet_transactions_source_created_idx
    ON wallet_transactions (source_account_id, created_at DESC);

CREATE INDEX wallet_transactions_destination_created_idx
    ON wallet_transactions (destination_account_id, created_at DESC);

CREATE INDEX wallet_transactions_created_idx
    ON wallet_transactions (created_at DESC);

-- Existing accounts (including myselfgoose) receive the standard starting wallet.
INSERT INTO wallets (account_id, balance, created_at, updated_at)
SELECT a.id, 200, NOW(), NOW()
FROM accounts a
WHERE NOT EXISTS (
    SELECT 1 FROM wallets w WHERE w.account_id = a.id
);

INSERT INTO wallet_transactions (
    id, type, status, amount, reason,
    source_account_id, source_balance_before, source_balance_after,
    failure_reason, created_at
)
SELECT gen_random_uuid(),
       'STARTING_BALANCE',
       'SUCCESS',
       200,
       'Initial competitor balance',
       a.id,
       0,
       200,
       '',
       NOW()
FROM accounts a
INNER JOIN wallets w ON w.account_id = a.id
WHERE NOT EXISTS (
    SELECT 1
    FROM wallet_transactions t
    WHERE t.source_account_id = a.id
      AND t.type = 'STARTING_BALANCE'
      AND t.status = 'SUCCESS'
);
