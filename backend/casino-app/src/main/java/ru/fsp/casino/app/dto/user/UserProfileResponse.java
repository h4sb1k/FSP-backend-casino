package ru.fsp.casino.app.dto.user;

import ru.fsp.casino.app.dto.history.RoundHistoryResponse;

import java.util.List;

public record UserProfileResponse(
    Long userId,
    String username,
    String vipTier,
    Long balance,
    Long reservedBalance,
    List<RoundHistoryResponse> history
) {}
