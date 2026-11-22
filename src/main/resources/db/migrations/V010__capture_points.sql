-- Capture point sessions, daily completions, temp blocks, and runtime pointer.

CREATE TABLE capture_sessions (
    capture_id              UUID PRIMARY KEY,
    point_id                TEXT NOT NULL,
    status                  TEXT NOT NULL,
    owning_team_id          UUID NULL REFERENCES teams(id) ON DELETE SET NULL,
    progress_millis         BIGINT NOT NULL DEFAULT 0 CHECK (progress_millis >= 0),
    reference_full_millis   BIGINT NOT NULL CHECK (reference_full_millis > 0),
    announced_thresholds    TEXT NOT NULL DEFAULT '',
    day_key                 TEXT NOT NULL,
    winning_team_id         UUID NULL REFERENCES teams(id) ON DELETE SET NULL,
    settlement_outcome      TEXT NOT NULL DEFAULT '',
    started_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    completed_at            TIMESTAMPTZ NULL
);

CREATE INDEX capture_sessions_day_idx ON capture_sessions (day_key, status);
CREATE INDEX capture_sessions_point_idx ON capture_sessions (point_id, started_at DESC);

CREATE TABLE capture_day_completions (
    day_key             TEXT NOT NULL,
    point_id            TEXT NOT NULL,
    capture_id          UUID NOT NULL REFERENCES capture_sessions(capture_id) ON DELETE CASCADE,
    winning_team_id     UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    completed_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (day_key, point_id)
);

CREATE TABLE capture_temp_blocks (
    world               TEXT NOT NULL,
    x                   INT NOT NULL,
    y                   INT NOT NULL,
    z                   INT NOT NULL,
    prior_block_data    TEXT NOT NULL,
    placed_material     TEXT NOT NULL DEFAULT '',
    placed_at           TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (world, x, y, z)
);

CREATE INDEX capture_temp_blocks_expires_idx ON capture_temp_blocks (expires_at);

CREATE TABLE capture_runtime (
    singleton_key       TEXT PRIMARY KEY,
    active_capture_id   UUID NULL REFERENCES capture_sessions(capture_id) ON DELETE SET NULL,
    paused              BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO capture_runtime (singleton_key, active_capture_id, paused, updated_at)
VALUES ('default', NULL, FALSE, NOW());
