package ru.fsp.casino.app.dto.admin;

import java.math.BigDecimal;

public record AdminConfigDto(
    Integer defaultMaxSlots,
    Long defaultEntryFee,
    Integer defaultPrizePoolPct,
    Boolean defaultBoostEnabled,
    Long defaultBoostCost,
    BigDecimal defaultBoostMultiplier,
    Integer waitingTimerSeconds
) {}
