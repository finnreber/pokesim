-- Battles are stored as one JSONB snapshot of the engine state plus a version column for optimistic locking.
-- Team data is COPIED into the snapshot when a battle starts, so editing or deleting a team never changes a battle.

CREATE TABLE battles (
    id          UUID PRIMARY KEY,
    player1_id  UUID        NOT NULL REFERENCES users(id),
    player2_id  UUID        REFERENCES users(id),        -- NULL while the lobby waits for an opponent
    phase       VARCHAR(24) NOT NULL,
    winner_id   UUID        REFERENCES users(id),        -- NULL while running, and for a draw
    seed        BIGINT      NOT NULL,                    -- server-side only: players must not be able to predict rolls
    state       JSONB       NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,          -- bumped on every update; stale writers are rejected
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (player2_id IS NULL OR player2_id <> player1_id)
);
CREATE INDEX idx_battles_phase_created ON battles(phase, created_at);
CREATE INDEX idx_battles_player1 ON battles(player1_id);
CREATE INDEX idx_battles_player2 ON battles(player2_id);

CREATE TABLE battle_events (
    id        BIGSERIAL PRIMARY KEY,
    battle_id UUID         NOT NULL REFERENCES battles(id) ON DELETE CASCADE,
    turn      INT          NOT NULL,
    type      VARCHAR(24)  NOT NULL,
    side      VARCHAR(2),
    message   VARCHAR(255) NOT NULL,
    amount    INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_battle_events_battle ON battle_events(battle_id, id);
