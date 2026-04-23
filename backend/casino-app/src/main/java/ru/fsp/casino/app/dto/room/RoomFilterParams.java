package ru.fsp.casino.app.dto.room;

public record RoomFilterParams(
    Long entryFeeMin,
    Long entryFeeMax,
    Integer seatsMin,
    Integer seatsMax,
    String tier,
    String status
) {}
