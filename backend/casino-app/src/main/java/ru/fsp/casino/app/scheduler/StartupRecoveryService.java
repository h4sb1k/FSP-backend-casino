package ru.fsp.casino.app.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.fsp.casino.domain.enums.RoomStatus;
import ru.fsp.casino.domain.model.Room;
import ru.fsp.casino.domain.model.RoomParticipant;
import ru.fsp.casino.domain.repository.RoomParticipantRepository;
import ru.fsp.casino.domain.repository.RoomRepository;
import ru.fsp.casino.domain.repository.UserRepository;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRecoveryService {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final UserRepository userRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverOnStartup() {
        List<Room> stuck = roomRepository.findByStatusIn(
            List.of(RoomStatus.WAITING, RoomStatus.RUNNING));

        if (stuck.isEmpty()) return;

        log.warn("[Recovery] Found {} unfinished room(s) after restart — refunding participants", stuck.size());

        for (Room room : stuck) {
            try {
                refundRoom(room);
            } catch (Exception e) {
                log.error("[Recovery] Failed to refund room {}: {}", room.getId(), e.getMessage(), e);
            }
        }
    }

    private void refundRoom(Room room) {
        List<RoomParticipant> participants = participantRepository.findByRoom_Id(room.getId());

        int refunded = 0;
        for (RoomParticipant p : participants) {
            if (Boolean.TRUE.equals(p.getIsBot()) || p.getUser() == null) continue;
            var user = p.getUser();
            // возвращаем entryFee: balance++ reservedBalance--
            user.setBalance(user.getBalance() + room.getEntryFee());
            user.setReservedBalance(Math.max(0, user.getReservedBalance() - room.getEntryFee()));
            userRepository.save(user);
            refunded++;
        }

        room.setStatus(RoomStatus.CANCELLED);
        room.setFinishedAt(Instant.now());
        roomRepository.save(room);

        log.warn("[Recovery] Room {} ({}) cancelled, {} participant(s) refunded {}pts each",
            room.getId(), room.getStatus(), refunded, room.getEntryFee());
    }
}
