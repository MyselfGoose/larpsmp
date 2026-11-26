-- Ace Hunt sessions, nominations, hunt assignments, scored deaths, lockouts, location snapshots.

CREATE TABLE ace_hunt_sessions (
    session_id              UUID PRIMARY KEY,
    phase                   TEXT NOT NULL,
    hunt_started_at         TIMESTAMPTZ NULL,
    surface_started_at      TIMESTAMPTZ NULL,
    ban_started_at          TIMESTAMPTZ NULL,
    ended_at                TIMESTAMPTZ NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL
);

CREATE TABLE ace_hunt_aces (
    session_id              UUID NOT NULL REFERENCES ace_hunt_sessions(session_id) ON DELETE CASCADE,
    team_id                 UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    account_id              UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    alive                   BOOLEAN NOT NULL DEFAULT TRUE,
    death_order             INT NULL,
    spectator               BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (session_id, team_id),
    UNIQUE (session_id, account_id)
);

CREATE TABLE ace_hunt_assignments (
    session_id              UUID NOT NULL REFERENCES ace_hunt_sessions(session_id) ON DELETE CASCADE,
    hunter_team_id          UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    target_team_id          UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    PRIMARY KEY (session_id, hunter_team_id),
    UNIQUE (session_id, target_team_id)
);

CREATE TABLE ace_hunt_scored_deaths (
    session_id              UUID NOT NULL REFERENCES ace_hunt_sessions(session_id) ON DELETE CASCADE,
    ace_team_id             UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    ace_account_id          UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    death_id                TEXT NOT NULL,
    killer_account_id       UUID NULL REFERENCES accounts(id) ON DELETE SET NULL,
    killer_team_id          UUID NULL REFERENCES teams(id) ON DELETE SET NULL,
    hunt_scored             BOOLEAN NOT NULL DEFAULT FALSE,
    death_order             INT NOT NULL,
    died_at                 TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, ace_team_id),
    UNIQUE (session_id, death_id)
);

CREATE INDEX ace_hunt_scored_deaths_order_idx
    ON ace_hunt_scored_deaths (session_id, death_order);

CREATE TABLE ace_hunt_lockouts (
    session_id              UUID NOT NULL REFERENCES ace_hunt_sessions(session_id) ON DELETE CASCADE,
    account_id              UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    release_at              TIMESTAMPTZ NOT NULL,
    hunter_spawn_team_id    UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    PRIMARY KEY (session_id, account_id)
);

CREATE TABLE ace_hunt_snapshots (
    session_id              UUID NOT NULL REFERENCES ace_hunt_sessions(session_id) ON DELETE CASCADE,
    account_id              UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    world_name              TEXT NOT NULL,
    x                       DOUBLE PRECISION NOT NULL,
    y                       DOUBLE PRECISION NOT NULL,
    z                       DOUBLE PRECISION NOT NULL,
    yaw                     REAL NOT NULL,
    pitch                   REAL NOT NULL,
    role                    TEXT NOT NULL,
    PRIMARY KEY (session_id, account_id)
);

CREATE TABLE ace_hunt_spectators (
    session_id              UUID NOT NULL REFERENCES ace_hunt_sessions(session_id) ON DELETE CASCADE,
    account_id              UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    reason                  TEXT NOT NULL,
    PRIMARY KEY (session_id, account_id)
);

CREATE TABLE ace_hunt_runtime (
    singleton_key           TEXT PRIMARY KEY,
    active_session_id       UUID NULL REFERENCES ace_hunt_sessions(session_id) ON DELETE SET NULL,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO ace_hunt_runtime (singleton_key, active_session_id, updated_at)
VALUES ('default', NULL, NOW());
