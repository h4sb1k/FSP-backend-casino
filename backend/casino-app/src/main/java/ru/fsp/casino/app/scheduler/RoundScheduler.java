package ru.fsp.casino.app.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.fsp.casino.app.websocket.RoomEventPublisher;
import ru.fsp.casino.app.redis.RoomCacheService;
import ru.fsp.casino.domain.enums.RoomStatus;
import ru.fsp.casino.domain.model.Room;
import ru.fsp.casino.domain.model.RoomParticipant;
import ru.fsp.casino.domain.model.RoundHistory;
import ru.fsp.casino.domain.repository.RoomParticipantRepository;
import ru.fsp.casino.domain.repository.RoomRepository;
import ru.fsp.casino.domain.repository.RoundHistoryRepository;
import ru.fsp.casino.game.dto.WinnerResult;
import ru.fsp.casino.game.service.BalanceService;
import ru.fsp.casino.game.service.BotService;
import ru.fsp.casino.game.service.WinnerService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoundScheduler {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final RoundHistoryRepository roundHistoryRepository;
    private final BotService botService;
    private final WinnerService winnerService;
    private final BalanceService balanceService;
    private final RoomEventPublisher eventPublisher;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final RoomCacheService roomCache;

    @Value("${app.game.waitingTimerSeconds:60}")
    private int waitingTimerSeconds;

    @Scheduled(fixedDelayString = "${app.game.schedulerIntervalMs:5000}")
    public void processPendingRooms() {
        Instant cutoff = Instant.now().minusSeconds(waitingTimerSeconds);
        List<Room> waitingRooms = roomRepository.findByStatus(RoomStatus.WAITING).stream()
            .filter(r -> r.getTimerStartedAt() != null)
            .toList();

        for (Room room : waitingRooms) {
            String timerKey = "room:timer:" + room.getId();
            Long ttl = redisTemplate.getExpire(timerKey, java.util.concurrent.TimeUnit.SECONDS);

            if (ttl != null && ttl > 0) {
                // Timer still running — publish a tick
                eventPublisher.publishTimerTick(room.getId(), ttl.intValue());
            } else {
                // Timer expired or key missing — check DB fallback
                boolean expired = ttl == null || ttl <= 0;
                boolean dbExpired = room.getTimerStartedAt().isBefore(cutoff);
                if (expired || dbExpired) {
                    try {
                        processRoom(room.getId());
                    } catch (Exception e) {
                        log.error("Failed to process room {}: {}", room.getId(), e.getMessage(), e);
                    }
                }
            }
        }
    }

    @Transactional
    public void processRoom(Long roomId) {
        String lockKey = "room:lock:" + roomId;
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(30));

        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Room {} already being processed by another instance", roomId);
            return;
        }

        try {
            processRoomInternal(roomId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void processRoomInternal(Long roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow();
        if (room.getStatus() != RoomStatus.WAITING) return;

        room.setStatus(RoomStatus.RUNNING);
        room.setRoundStartedAt(Instant.now());
        roomRepository.save(room);

        botService.fillWithBots(room);
        List<RoomParticipant> allParticipants = participantRepository.findByRoom_Id(roomId);

        eventPublisher.publishParticipantUpdate(roomId, allParticipants);

        WinnerResult result = winnerService.determineWinner(room, allParticipants);
        long payout = balanceService.settle(room, allParticipants, result.winner());

        long realCount = allParticipants.stream().filter(p -> !Boolean.TRUE.equals(p.getIsBot())).count();
        long botCount = allParticipants.size() - realCount;

        RoundHistory history = RoundHistory.builder()
            .roomId(roomId)
            .winnerParticipantId(result.winner().getId())
            .winnerUserId(Boolean.TRUE.equals(result.winner().getIsBot()) ? null
                : result.winner().getUser() != null ? result.winner().getUser().getId() : null)
            .winnerIsBot(Boolean.TRUE.equals(result.winner().getIsBot()))
            .totalPool(realCount * room.getEntryFee())
            .payout(payout)
            .rngSeed(result.seed())
            .rngRoll(BigDecimal.valueOf(result.roll()))
            .rngTotalWeight(BigDecimal.valueOf(result.totalWeight()))
            .participantCount(allParticipants.size())
            .botCount((int) botCount)
            .finishedAt(Instant.now())
            .build();
        roundHistoryRepository.save(history);

        room.setStatus(RoomStatus.FINISHED);
        room.setFinishedAt(Instant.now());
        room.setWinnerParticipantId(result.winner().getId());
        roomRepository.save(room);

        roomCache.evictRoom(roomId);
        roomCache.evictRoomsList();
        redisTemplate.delete("room:timer:" + roomId);

        String winnerName = !Boolean.TRUE.equals(result.winner().getIsBot()) && result.winner().getUser() != null
            ? result.winner().getUser().getUsername() : null;
        eventPublisher.publishRoundResult(
            roomId,
            result.winner().getId(),
            winnerName,
            Boolean.TRUE.equals(result.winner().getIsBot()),
            payout,
            result.roll(),
            result.totalWeight()
        );

        if (!Boolean.TRUE.equals(result.winner().getIsBot()) && result.winner().getUser() != null) {
            eventPublisher.publishBalanceUpdate(
                result.winner().getUser().getId(),
                result.winner().getUser().getBalance()
            );
        }

        log.info("Room {} finished. Winner: {} (bot={}), payout={}",
            roomId, result.winner().getId(),
            result.winner().getIsBot(), payout);
    }
}
