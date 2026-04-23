package ru.fsp.casino.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.fsp.casino.domain.enums.RoomStatus;
import ru.fsp.casino.domain.enums.VipTier;
import ru.fsp.casino.domain.model.Room;
import ru.fsp.casino.domain.model.RoomParticipant;
import ru.fsp.casino.domain.model.User;
import ru.fsp.casino.game.dto.WinnerResult;
import ru.fsp.casino.game.service.WinnerService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WinnerServiceTest {

    private WinnerService winnerService;
    private Room room;

    @BeforeEach
    void setUp() {
        winnerService = new WinnerService();
        room = Room.builder()
            .id(1L)
            .status(RoomStatus.WAITING)
            .maxSlots(6)
            .entryFee(100L)
            .prizePoolPct(80)
            .boostEnabled(true)
            .boostMultiplier(BigDecimal.valueOf(2.0))
            .build();
    }

    @Test
    void determineWinner_allEqualWeights_uniformDistribution() {
        List<RoomParticipant> participants = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            participants.add(RoomParticipant.builder()
                .id((long) i)
                .isBot(false)
                .boosted(false)
                .build());
        }

        Map<Long, Integer> wins = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            WinnerResult result = winnerService.determineWinner(room, participants);
            wins.merge(result.winner().getId(), 1, Integer::sum);
        }

        assertEquals(6, wins.size(), "All 6 participants should win at least once in 1000 runs");
    }

    @Test
    void determineWinner_withBoost_higherWinRate() {
        List<RoomParticipant> participants = new ArrayList<>();
        RoomParticipant boosted = RoomParticipant.builder()
            .id(0L).isBot(false).boosted(true).build();
        participants.add(boosted);
        for (int i = 1; i < 6; i++) {
            participants.add(RoomParticipant.builder()
                .id((long) i).isBot(false).boosted(false).build());
        }

        int boostedWins = 0;
        for (int i = 0; i < 1000; i++) {
            WinnerResult result = winnerService.determineWinner(room, participants);
            if (result.winner().getId().equals(0L)) boostedWins++;
        }

        // boosted weight=2, others=1 each → expected ~2/7 ≈ 28.6%
        assertTrue(boostedWins > 250, "Boosted participant should win >25% in 1000 runs, got: " + boostedWins);
    }

    @Test
    void determineWinner_singleParticipant_alwaysWins() {
        RoomParticipant only = RoomParticipant.builder()
            .id(1L).isBot(false).boosted(false).build();
        room = Room.builder()
            .id(1L).maxSlots(1).entryFee(100L).prizePoolPct(80)
            .boostMultiplier(BigDecimal.valueOf(2.0)).build();

        for (int i = 0; i < 100; i++) {
            WinnerResult result = winnerService.determineWinner(room, List.of(only));
            assertEquals(1L, result.winner().getId());
        }
    }

    @Test
    void calculateWinProbability_withBoost_returnsCorrectValue() {
        // 6 slots, boostMultiplier=2.0, hasBust=true → 2/(5+2) = 2/7 ≈ 0.2857
        double prob = winnerService.calculateWinProbability(room, true);
        assertEquals(2.0 / 7.0, prob, 0.0001);
    }

    @Test
    void calculateWinProbability_withoutBoost_returnsCorrectValue() {
        // 6 slots, hasBust=false → 1/6 ≈ 0.1667
        double prob = winnerService.calculateWinProbability(room, false);
        assertEquals(1.0 / 6.0, prob, 0.0001);
    }
}
