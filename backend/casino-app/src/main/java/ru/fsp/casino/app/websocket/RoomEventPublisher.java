package ru.fsp.casino.app.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import ru.fsp.casino.domain.model.RoomParticipant;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishParticipantUpdate(Long roomId, List<RoomParticipant> participants) {
        var payload = Map.of(
            "type", "PARTICIPANT_UPDATE",
            "roomId", roomId,
            "participants", participants.stream().map(p -> Map.of(
                "id", p.getId(),
                "userId", p.getUser() != null ? p.getUser().getId() : null,
                "username", p.getUser() != null ? p.getUser().getUsername() : null,
                "isBot", p.getIsBot(),
                "botName", p.getBotName() != null ? p.getBotName() : "",
                "boosted", p.getBoosted()
            )).toList(),
            "seatsFilled", (long) participants.size()
        );
        messagingTemplate.convertAndSend("/topic/room/" + roomId, payload);
        log.debug("[WS] PARTICIPANT_UPDATE roomId={} count={}", roomId, participants.size());
    }

    public void publishRoundResult(Long roomId, Long winnerParticipantId, String winnerName,
                                   boolean winnerIsBot, long payout, double rngRoll, double rngTotalWeight) {
        var payload = Map.of(
            "type", "ROUND_RESULT",
            "roomId", roomId,
            "winnerParticipantId", winnerParticipantId,
            "winnerDisplayName", winnerName != null ? winnerName : "",
            "winnerIsBot", winnerIsBot,
            "payout", payout,
            "rngRoll", rngRoll,
            "rngTotalWeight", rngTotalWeight
        );
        messagingTemplate.convertAndSend("/topic/room/" + roomId, payload);
        log.debug("[WS] ROUND_RESULT roomId={} winner={} payout={}", roomId, winnerName, payout);
    }

    public void publishTimerTick(Long roomId, int secondsRemaining) {
        var payload = Map.of(
            "type", "TIMER_TICK",
            "roomId", roomId,
            "secondsRemaining", secondsRemaining
        );
        messagingTemplate.convertAndSend("/topic/room/" + roomId, payload);
    }

    public void publishBalanceUpdate(Long userId, long newBalance) {
        var payload = Map.of(
            "type", "BALANCE_UPDATE",
            "userId", userId,
            "newBalance", newBalance
        );
        messagingTemplate.convertAndSend("/topic/user/" + userId, payload);
        log.debug("[WS] BALANCE_UPDATE userId={} balance={}", userId, newBalance);
    }

    public void publishRoomsListUpdate(Object payload) {
        messagingTemplate.convertAndSend("/topic/rooms-list", Map.of("type", "ROOM_CREATED"));
        log.debug("[WS] ROOMS_LIST_UPDATE");
    }
}
