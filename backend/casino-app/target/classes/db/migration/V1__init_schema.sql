CREATE TABLE users (
    id               BIGSERIAL    PRIMARY KEY,
    username         VARCHAR(100) NOT NULL UNIQUE,
    passwordHash     VARCHAR(255) NOT NULL,
    vipTier          VARCHAR(20)  NOT NULL DEFAULT 'STANDARD',
    role             VARCHAR(20)  NOT NULL DEFAULT 'USER',
    balance          BIGINT       NOT NULL DEFAULT 0,
    reservedBalance  BIGINT       NOT NULL DEFAULT 0,
    createdAt        TIMESTAMPTZ  DEFAULT NOW()
);

CREATE TABLE rooms (
    id                    BIGSERIAL   PRIMARY KEY,
    status                VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    tier                  VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    maxSlots              INT         NOT NULL DEFAULT 6,
    entryFee              BIGINT      NOT NULL,
    prizePoolPct          INT         NOT NULL DEFAULT 80,
    boostEnabled          BOOLEAN     NOT NULL DEFAULT TRUE,
    boostCost             BIGINT,
    boostMultiplier       NUMERIC(4,2) NOT NULL DEFAULT 2.0,
    creatorId             BIGINT      REFERENCES users(id),
    timerStartedAt        TIMESTAMPTZ,
    roundStartedAt        TIMESTAMPTZ,
    finishedAt            TIMESTAMPTZ,
    winnerParticipantId   BIGINT,
    createdAt             TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE room_participants (
    id        BIGSERIAL   PRIMARY KEY,
    roomId    BIGINT      NOT NULL REFERENCES rooms(id),
    userId    BIGINT      REFERENCES users(id),
    isBot     BOOLEAN     NOT NULL DEFAULT FALSE,
    botName   VARCHAR(100),
    boosted   BOOLEAN     NOT NULL DEFAULT FALSE,
    joinedAt  TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (roomId, userId)
);

ALTER TABLE rooms ADD CONSTRAINT fk_winner
    FOREIGN KEY (winnerParticipantId) REFERENCES room_participants(id);

CREATE TABLE round_history (
    id                    BIGSERIAL   PRIMARY KEY,
    roomId                BIGINT      NOT NULL REFERENCES rooms(id),
    winnerUserId          BIGINT      REFERENCES users(id),
    winnerParticipantId   BIGINT      REFERENCES room_participants(id),
    winnerIsBot           BOOLEAN     NOT NULL DEFAULT FALSE,
    totalPool             BIGINT      NOT NULL,
    payout                BIGINT      NOT NULL,
    rngSeed               BIGINT,
    rngRoll               NUMERIC(18,6),
    rngTotalWeight        NUMERIC(18,6),
    participantCount      INT,
    botCount              INT,
    finishedAt            TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE admin_config (
    id                      INT PRIMARY KEY DEFAULT 1,
    defaultMaxSlots         INT          NOT NULL DEFAULT 6,
    defaultEntryFee         BIGINT       NOT NULL DEFAULT 100,
    defaultPrizePoolPct     INT          NOT NULL DEFAULT 80,
    defaultBoostEnabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    defaultBoostCost        BIGINT       NOT NULL DEFAULT 50,
    defaultBoostMultiplier  NUMERIC(4,2) NOT NULL DEFAULT 2.0,
    waitingTimerSeconds     INT          NOT NULL DEFAULT 60,
    updatedAt               TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_rooms_status        ON rooms(status);
CREATE INDEX idx_rooms_tier          ON rooms(tier);
CREATE INDEX idx_participants_room   ON room_participants(roomId);
CREATE INDEX idx_participants_user   ON room_participants(userId);
CREATE INDEX idx_history_room        ON round_history(roomId);
CREATE INDEX idx_history_finished    ON round_history(finishedAt DESC);
