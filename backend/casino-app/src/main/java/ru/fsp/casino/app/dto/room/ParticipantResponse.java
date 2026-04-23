package ru.fsp.casino.app.dto.room;

public record ParticipantResponse(
    Long id,
    Long userId,
    String username,
    Boolean isBot,
    String botName,
    Boolean boosted
) {}
