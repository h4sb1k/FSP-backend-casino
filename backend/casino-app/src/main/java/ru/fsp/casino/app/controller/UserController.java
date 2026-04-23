package ru.fsp.casino.app.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.fsp.casino.app.dto.history.RoundHistoryResponse;
import ru.fsp.casino.app.dto.room.RoomResponse;
import ru.fsp.casino.app.dto.user.UserProfileResponse;
import ru.fsp.casino.domain.enums.RoomStatus;
import ru.fsp.casino.domain.model.RoundHistory;
import ru.fsp.casino.domain.model.User;
import ru.fsp.casino.domain.repository.RoomParticipantRepository;
import ru.fsp.casino.domain.repository.RoundHistoryRepository;
import ru.fsp.casino.domain.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final RoomParticipantRepository participantRepository;
    private final RoundHistoryRepository roundHistoryRepository;
    private final RoomController roomController;

    @GetMapping("/{userId}/active-room")
    public ResponseEntity<RoomResponse> getActiveRoom(@PathVariable Long userId, Authentication auth) {
        var active = participantRepository.findActiveByUserId(
            userId, List.of(RoomStatus.WAITING, RoomStatus.RUNNING));
        if (active.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(roomController.toResponse(active.get(0).getRoom()));
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileResponse> getProfile(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {

        Long requesterId = (Long) auth.getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();

        List<RoundHistory> history = roundHistoryRepository
            .findByWinnerUserIdOrderByFinishedAtDesc(userId, PageRequest.of(0, limit));

        boolean isSelf = requesterId.equals(userId);
        List<RoundHistoryResponse> historyDtos = history.stream()
            .map(h -> new RoundHistoryResponse(
                h.getRoomId(), user.getUsername(), h.getWinnerIsBot(),
                h.getTotalPool(), h.getPayout(), h.getParticipantCount(), h.getBotCount(),
                h.getRngRoll() != null ? h.getRngRoll().doubleValue() : null,
                h.getRngTotalWeight() != null ? h.getRngTotalWeight().doubleValue() : null,
                h.getFinishedAt()))
            .toList();

        return ResponseEntity.ok(new UserProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getVipTier().name(),
            isSelf ? user.getBalance() : null,
            isSelf ? user.getReservedBalance() : null,
            historyDtos
        ));
    }
}
