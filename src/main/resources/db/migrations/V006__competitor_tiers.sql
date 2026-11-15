-- Account-scoped competitor tiers (1 strongest … 4). Survives team moves; clearable.

CREATE TABLE competitor_tiers (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    tier       SMALLINT NOT NULL CHECK (tier BETWEEN 1 AND 4),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX competitor_tiers_tier_idx ON competitor_tiers (tier);
