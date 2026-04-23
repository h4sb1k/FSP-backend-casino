package ru.fsp.casino.app.dto.auth;

public record TokenResponse(
    String token,
    Long userId,
    String username,
    String vipTier,
    String role,
    Long balance,
    Long reservedBalance
) {}
