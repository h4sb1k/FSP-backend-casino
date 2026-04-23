package ru.fsp.casino.app.dto.room;

import java.time.Instant;
import java.util.List;

public record RoomResponse(
    Long id,
    String status,
    String tier,
    Integer maxSlots,
    Integer seatsFilled,
    Long entryFee,
    Integer prizePoolPct,
    Boolean boostEnabled,
    Long boostCost,
    Double boostMultiplier,
    List<ParticipantResponse> participants,
    Instant timerStartedAt,
    Instant createdAt
) {}
