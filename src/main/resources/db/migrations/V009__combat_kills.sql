-- Attributed PvP kills for settlement audit and future /top kills (exclude event_kill).

CREATE TABLE combat_kills (
    death_id            TEXT PRIMARY KEY,
    victim_account_id   UUID NOT NULL REFERENCES accounts(id),
    killer_account_id   UUID NOT NULL REFERENCES accounts(id),
    cause               TEXT NOT NULL,
    evidence            TEXT NOT NULL DEFAULT '',
    event_kill          BOOLEAN NOT NULL DEFAULT FALSE,
    friendly_fire       BOOLEAN NOT NULL DEFAULT FALSE,
    settlement_outcome  TEXT NOT NULL DEFAULT '',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX combat_kills_killer_normal_idx
    ON combat_kills (killer_account_id, created_at DESC)
    WHERE event_kill = FALSE AND friendly_fire = FALSE;

CREATE INDEX combat_kills_created_idx ON combat_kills (created_at DESC);
