CREATE TABLE players (
    uuid       CHAR(36)    NOT NULL,
    username   VARCHAR(16) NOT NULL,
    first_seen TIMESTAMP   NOT NULL,
    last_seen  TIMESTAMP   NOT NULL,
    PRIMARY KEY (uuid)
);

CREATE INDEX idx_players_username ON players (username);

CREATE TABLE ratings (
    uuid        CHAR(36)    NOT NULL,
    mode_id     VARCHAR(32) NOT NULL,
    team_format VARCHAR(8)  NOT NULL,
    rating      INT         NOT NULL,
    peak_rating INT         NOT NULL,
    games       INT         NOT NULL,
    wins        INT         NOT NULL,
    losses      INT         NOT NULL,
    streak      INT         NOT NULL,
    updated_at  TIMESTAMP   NOT NULL,
    PRIMARY KEY (uuid, mode_id, team_format),
    CONSTRAINT fk_ratings_player FOREIGN KEY (uuid) REFERENCES players (uuid) ON DELETE CASCADE
);

CREATE INDEX idx_ratings_leaderboard ON ratings (mode_id, team_format, rating);
