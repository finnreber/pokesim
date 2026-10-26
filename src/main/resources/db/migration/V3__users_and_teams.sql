-- Accounts and teams. New tables use INT for small numbers so Hibernate's schema validation needs no special mapping.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL UNIQUE,      -- stored lowercase; the API normalises before every lookup
    password_hash VARCHAR(100) NOT NULL,             -- BCrypt, never the password
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE teams (
    id         UUID PRIMARY KEY,
    owner_id   UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(40)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (owner_id, name)
);

CREATE TABLE team_members (
    id             UUID PRIMARY KEY,
    team_id        UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    party_position INT  NOT NULL CHECK (party_position BETWEEN 0 AND 5),
    species_id     INT  NOT NULL REFERENCES pokemon_species(id),
    level          INT  NOT NULL CHECK (level BETWEEN 1 AND 100),
    UNIQUE (team_id, party_position)
);

CREATE TABLE team_member_moves (
    member_id UUID NOT NULL REFERENCES team_members(id) ON DELETE CASCADE,
    slot      INT  NOT NULL CHECK (slot BETWEEN 0 AND 3),
    move_id   INT  NOT NULL REFERENCES moves(id),
    PRIMARY KEY (member_id, slot)
);

CREATE INDEX idx_teams_owner ON teams(owner_id);
CREATE INDEX idx_team_members_team ON team_members(team_id);
