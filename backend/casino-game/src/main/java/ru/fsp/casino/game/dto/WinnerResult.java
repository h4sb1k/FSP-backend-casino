package ru.fsp.casino.game.dto;

import ru.fsp.casino.domain.model.RoomParticipant;

public record WinnerResult(
    RoomParticipant winner,
    double roll,
    double totalWeight,
    long seed
) {}
