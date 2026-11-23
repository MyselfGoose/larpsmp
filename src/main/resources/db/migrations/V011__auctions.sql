-- Mystery shulker auction sessions, fixed representatives, lots, and bid history.

CREATE TABLE auction_sessions (
    session_id          UUID PRIMARY KEY,
    status              TEXT NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    closed_at           TIMESTAMPTZ NULL
);

CREATE TABLE auction_representatives (
    session_id          UUID NOT NULL REFERENCES auction_sessions(session_id) ON DELETE CASCADE,
    team_id             UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    account_id          UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    registered_at       TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, team_id),
    UNIQUE (session_id, account_id)
);

CREATE INDEX auction_representatives_account_idx
    ON auction_representatives (session_id, account_id);

CREATE TABLE auction_lots (
    lot_id                      UUID PRIMARY KEY,
    session_id                  UUID NOT NULL REFERENCES auction_sessions(session_id) ON DELETE CASCADE,
    lot_number                  INT NOT NULL CHECK (lot_number > 0),
    theme                       TEXT NOT NULL,
    starting_bid                BIGINT NOT NULL CHECK (starting_bid >= 0),
    status                      TEXT NOT NULL,
    high_bid                    BIGINT NULL CHECK (high_bid IS NULL OR high_bid >= 0),
    high_bidder_account_id      UUID NULL REFERENCES accounts(id) ON DELETE SET NULL,
    owed_account_id             UUID NULL REFERENCES accounts(id) ON DELETE SET NULL,
    owed_amount                 BIGINT NULL CHECK (owed_amount IS NULL OR owed_amount >= 0),
    payment_transaction_id      UUID NULL,
    excluded_payer_ids          TEXT NOT NULL DEFAULT '',
    opened_at                   TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    closed_at                   TIMESTAMPTZ NULL,
    paid_at                     TIMESTAMPTZ NULL,
    delivered_at                TIMESTAMPTZ NULL,
    UNIQUE (session_id, lot_number)
);

CREATE INDEX auction_lots_session_status_idx ON auction_lots (session_id, status);

CREATE TABLE auction_bids (
    bid_id              UUID PRIMARY KEY,
    lot_id              UUID NOT NULL REFERENCES auction_lots(lot_id) ON DELETE CASCADE,
    account_id          UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    team_id             UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    amount              BIGINT NOT NULL CHECK (amount > 0),
    created_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX auction_bids_lot_created_idx ON auction_bids (lot_id, created_at DESC);

CREATE TABLE auction_runtime (
    singleton_key       TEXT PRIMARY KEY,
    active_session_id   UUID NULL REFERENCES auction_sessions(session_id) ON DELETE SET NULL,
    active_lot_id       UUID NULL REFERENCES auction_lots(lot_id) ON DELETE SET NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO auction_runtime (singleton_key, active_session_id, active_lot_id, updated_at)
VALUES ('default', NULL, NULL, NOW());
