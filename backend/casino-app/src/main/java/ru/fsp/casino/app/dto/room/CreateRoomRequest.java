package ru.fsp.casino.app.dto.room;

import jakarta.validation.constraints.*;

public record CreateRoomRequest(
    String tier,
    @NotNull @Min(2) @Max(10) Integer maxSlots,
    @NotNull @Positive Long entryFee,
    @Min(0) @Max(100) Integer prizePoolPct,
    Boolean boostEnabled,
    Long boostCost,
    Double boostMultiplier
) {}
