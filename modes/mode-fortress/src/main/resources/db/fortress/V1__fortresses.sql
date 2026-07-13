-- Fortress owns this table. The engine knows nothing about it.
--
-- One row per SLOT, not per fortress: a player has a fixed number of slots (3 today, it is
-- a config knob), and a slot is either empty or holds a build. The (owner, slot) primary
-- key is what makes "you cannot have four fortresses" a fact of the schema rather than a
-- check somebody can forget to write.
--
-- Keep it portable: this runs on MySQL in production and on H2 in the tests.

CREATE TABLE fortresses (
    owner_uuid CHAR(36)     NOT NULL,
    slot       INT          NOT NULL,
    name       VARCHAR(32)  NOT NULL,

    -- The build itself: palette + cube + crystal, gzipped (see BlueprintCodec).
    -- An empty 20x20x20 is under a hundred bytes; a dense one, a few kilobytes.
    blueprint  BLOB         NOT NULL,

    -- Denormalised on purpose, so the menu can list slots without decoding every blueprint:
    -- what size cube it was built at, whether it is playable, how many blocks it uses.
    size        INT         NOT NULL,
    playable    BOOLEAN     NOT NULL,
    block_count INT         NOT NULL,

    -- Exactly one slot per player is the default. Enforced in code (SQL cannot express
    -- "at most one true per owner" portably), and repaired on read if it ever drifts.
    is_default BOOLEAN      NOT NULL,

    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,

    PRIMARY KEY (owner_uuid, slot)
);

CREATE INDEX idx_fortresses_owner ON fortresses (owner_uuid);
