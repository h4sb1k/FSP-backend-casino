package ru.fsp.casino.app.dto.room;

public record BoostResponse(
    Double winProbability,
    Double boostMultiplier,
    Long boostCost
) {}
