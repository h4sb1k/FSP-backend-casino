package ru.fsp.casino.game.dto;

import ru.fsp.casino.domain.model.RoomParticipant;

public record RoundSummary(
    Long roomId,
    RoomParticipant winner,
    long totalPool,
    long payout,
    int participantCount,
    int botCount,
    double rngRoll,
    double rngTotalWeight,
    long rngSeed
) {}
