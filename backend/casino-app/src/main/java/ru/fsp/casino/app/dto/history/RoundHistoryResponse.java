package ru.fsp.casino.app.dto.history;

import java.time.Instant;

public record RoundHistoryResponse(
    Long roomId,
    String winnerDisplayName,
    Boolean winnerIsBot,
    Long totalPool,
    Long payout,
    Integer participantCount,
    Integer botCount,
    Double rngRoll,
    Double rngTotalWeight,
    Instant finishedAt
) {}
