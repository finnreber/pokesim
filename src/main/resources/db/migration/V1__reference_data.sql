-- Reference data (read-only at runtime). Seeded from PokeAPI by the seeder; the API never calls PokeAPI.
-- "generation" on each row is what makes the dataset extensible: adding Gen 2 = inserting rows, not new tables.

CREATE TABLE pokemon_species (
    id              SERIAL PRIMARY KEY,
    dex_number      INT          NOT NULL UNIQUE,
    name            VARCHAR(64)  NOT NULL UNIQUE,
    generation      SMALLINT     NOT NULL,
    primary_type    VARCHAR(16)  NOT NULL,
    secondary_type  VARCHAR(16),
    base_hp         SMALLINT     NOT NULL,
    base_attack     SMALLINT     NOT NULL,
    base_defense    SMALLINT     NOT NULL,
    base_sp_attack  SMALLINT     NOT NULL,
    base_sp_defense SMALLINT     NOT NULL,
    base_speed      SMALLINT     NOT NULL
);

CREATE TABLE moves (
    id               SERIAL PRIMARY KEY,
    name             VARCHAR(64) NOT NULL UNIQUE,
    generation       SMALLINT    NOT NULL,
    type             VARCHAR(16) NOT NULL,
    power            SMALLINT    NOT NULL DEFAULT 0,
    accuracy         SMALLINT,                       -- NULL = never misses
    pp               SMALLINT    NOT NULL,
    priority         SMALLINT    NOT NULL DEFAULT 0,
    ailment          VARCHAR(16) NOT NULL DEFAULT 'NONE',
    ailment_chance   SMALLINT    NOT NULL DEFAULT 0,
    recoil_divisor   SMALLINT    NOT NULL DEFAULT 0
);

-- Which species can learn which move in which game (PokeAPI "version group", e.g. firered-leafgreen).
CREATE TABLE learnable_move (
    species_id    INT         NOT NULL REFERENCES pokemon_species(id),
    move_id       INT         NOT NULL REFERENCES moves(id),
    version_group VARCHAR(32) NOT NULL,
    PRIMARY KEY (species_id, move_id, version_group)
);
CREATE INDEX idx_learnable_move_move ON learnable_move(move_id);
