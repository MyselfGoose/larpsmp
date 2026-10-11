-- Teams, captains, and roster memberships (keyed by permanent account IDs).

CREATE TABLE teams (
    id                   UUID PRIMARY KEY,
    slug                 TEXT NOT NULL,
    display_name         TEXT NOT NULL,
    color                TEXT NOT NULL,
    sort_order           INT NOT NULL DEFAULT 0,
    captain_account_id   UUID NULL REFERENCES accounts(id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX teams_slug_unique ON teams (slug);
CREATE INDEX teams_sort_order_idx ON teams (sort_order);

CREATE TABLE team_memberships (
    team_id      UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    account_id   UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    sort_order   INT NOT NULL DEFAULT 0,
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (team_id, account_id)
);

CREATE UNIQUE INDEX team_memberships_account_unique ON team_memberships (account_id);
CREATE INDEX team_memberships_team_id_idx ON team_memberships (team_id);

-- Seed the standard competition teams when the table is empty.
INSERT INTO teams (id, slug, display_name, color, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), 'blue', 'Blue', 'blue', 0, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM teams WHERE slug = 'blue');

INSERT INTO teams (id, slug, display_name, color, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), 'red', 'Red', 'red', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM teams WHERE slug = 'red');

INSERT INTO teams (id, slug, display_name, color, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), 'yellow', 'Yellow', 'yellow', 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM teams WHERE slug = 'yellow');
