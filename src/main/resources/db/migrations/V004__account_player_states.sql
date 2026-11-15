-- Account-keyed Minecraft body state (inventory, location, vitals, XP, effects).
-- Vanilla playerdata is not the source of truth after successful auth.

CREATE TABLE account_player_states (
    account_id          UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    schema_version      INT NOT NULL DEFAULT 1,
    world_name          TEXT NOT NULL,
    x                   DOUBLE PRECISION NOT NULL,
    y                   DOUBLE PRECISION NOT NULL,
    z                   DOUBLE PRECISION NOT NULL,
    yaw                 REAL NOT NULL,
    pitch               REAL NOT NULL,
    inventory_data      BYTEA NOT NULL,
    armor_data          BYTEA NOT NULL,
    offhand_data        BYTEA NOT NULL,
    ender_chest_data    BYTEA NOT NULL,
    health              DOUBLE PRECISION NOT NULL,
    food_level          INT NOT NULL,
    saturation          REAL NOT NULL,
    exhaustion          REAL NOT NULL,
    level               INT NOT NULL,
    total_experience    INT NOT NULL,
    exp                 REAL NOT NULL,
    game_mode           TEXT NOT NULL,
    potion_effects_data BYTEA NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
