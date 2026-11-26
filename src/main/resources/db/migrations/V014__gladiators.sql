-- Gladiators sessions, schedule, fights, quota, kits, body snapshots, barriers.

CREATE TABLE gladiator_sessions (
    session_id              UUID PRIMARY KEY,
    phase                   TEXT NOT NULL,
    schedule_index          INT NOT NULL DEFAULT 0,
    payout_completed        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    ended_at                TIMESTAMPTZ NULL
);

CREATE TABLE gladiator_schedule (
    session_id              UUID NOT NULL REFERENCES gladiator_sessions(session_id) ON DELETE CASCADE,
    bout_index              INT NOT NULL,
    team_a_id               UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    team_b_id               UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    status                  TEXT NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (session_id, bout_index)
);

CREATE TABLE gladiator_fights (
    fight_id                UUID PRIMARY KEY,
    session_id              UUID NOT NULL REFERENCES gladiator_sessions(session_id) ON DELETE CASCADE,
    fight_kind              TEXT NOT NULL,
    bout_index              INT NULL,
    team_a_id               UUID NULL REFERENCES teams(id) ON DELETE SET NULL,
    team_b_id               UUID NULL REFERENCES teams(id) ON DELETE SET NULL,
    kit_id                  TEXT NOT NULL,
    status                  TEXT NOT NULL,
    winning_team_id         UUID NULL REFERENCES teams(id) ON DELETE SET NULL,
    started_at              TIMESTAMPTZ NOT NULL,
    ended_at                TIMESTAMPTZ NULL
);

CREATE INDEX gladiator_fights_session_idx
    ON gladiator_fights (session_id, started_at);

CREATE TABLE gladiator_fighters (
    fight_id                UUID NOT NULL REFERENCES gladiator_fights(fight_id) ON DELETE CASCADE,
    account_id              UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    team_id                 UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    slot_index              INT NOT NULL,
    alive                   BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (fight_id, account_id)
);

CREATE TABLE gladiator_lineups (
    session_id              UUID NOT NULL REFERENCES gladiator_sessions(session_id) ON DELETE CASCADE,
    team_id                 UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    slot_index              INT NOT NULL,
    account_id              UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    PRIMARY KEY (session_id, team_id, slot_index)
);

CREATE TABLE gladiator_quota (
    session_id              UUID NOT NULL REFERENCES gladiator_sessions(session_id) ON DELETE CASCADE,
    account_id              UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    fights_used             INT NOT NULL DEFAULT 0,
    PRIMARY KEY (session_id, account_id)
);

CREATE TABLE gladiator_kit_bans (
    session_id              UUID NOT NULL REFERENCES gladiator_sessions(session_id) ON DELETE CASCADE,
    ban_order               INT NOT NULL,
    kit_id                  TEXT NOT NULL,
    banned_by_account_id    UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    PRIMARY KEY (session_id, ban_order)
);

CREATE TABLE gladiator_duel_pending (
    session_id              UUID PRIMARY KEY REFERENCES gladiator_sessions(session_id) ON DELETE CASCADE,
    fighter_a_account_id    UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    fighter_b_account_id    UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    fighter_a_team_id       UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    fighter_b_team_id       UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    next_ban_account_id     UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE TABLE gladiator_body_snapshots (
    session_id              UUID NOT NULL REFERENCES gladiator_sessions(session_id) ON DELETE CASCADE,
    account_id              UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    world_name              TEXT NOT NULL,
    x                       DOUBLE PRECISION NOT NULL,
    y                       DOUBLE PRECISION NOT NULL,
    z                       DOUBLE PRECISION NOT NULL,
    yaw                     REAL NOT NULL,
    pitch                   REAL NOT NULL,
    inventory_data          BYTEA NOT NULL,
    armor_data              BYTEA NOT NULL,
    offhand_data            BYTEA NOT NULL,
    ender_chest_data        BYTEA NOT NULL,
    health                  DOUBLE PRECISION NOT NULL,
    food_level              INT NOT NULL,
    saturation              REAL NOT NULL,
    exhaustion              REAL NOT NULL,
    level                   INT NOT NULL,
    total_experience        INT NOT NULL,
    exp                     REAL NOT NULL,
    game_mode               TEXT NOT NULL,
    potion_effects_data     BYTEA NOT NULL,
    PRIMARY KEY (session_id, account_id)
);

CREATE TABLE gladiator_kits (
    kit_id                  TEXT PRIMARY KEY,
    inventory_data          BYTEA NOT NULL,
    armor_data              BYTEA NOT NULL,
    offhand_data            BYTEA NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL
);

CREATE TABLE gladiator_barrier_blocks (
    world_name              TEXT NOT NULL,
    x                       INT NOT NULL,
    y                       INT NOT NULL,
    z                       INT NOT NULL,
    prior_block_data        TEXT NOT NULL,
    PRIMARY KEY (world_name, x, y, z)
);

CREATE TABLE gladiator_runtime (
    singleton_key           TEXT PRIMARY KEY,
    active_session_id       UUID NULL REFERENCES gladiator_sessions(session_id) ON DELETE SET NULL,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO gladiator_runtime (singleton_key, active_session_id, updated_at)
VALUES ('default', NULL, NOW());
