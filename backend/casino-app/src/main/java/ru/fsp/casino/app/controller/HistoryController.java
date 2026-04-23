package ru.fsp.casino.app.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.fsp.casino.app.dto.history.RoundHistoryResponse;
import ru.fsp.casino.domain.model.RoundHistory;
import ru.fsp.casino.domain.model.User;
import ru.fsp.casino.domain.repository.RoundHistoryRepository;
import ru.fsp.casino.domain.repository.UserRepository;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final RoundHistoryRepository roundHistoryRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<RoundHistoryResponse>> getHistory(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Long roomId,
            Authentication auth) {

        PageRequest page = PageRequest.of(0, Math.min(limit, 200));
        List<RoundHistory> records = roomId != null
            ? roundHistoryRepository.findByRoomIdOrderByFinishedAtDesc(roomId, page)
            : roundHistoryRepository.findByOrderByFinishedAtDesc(page);

        return ResponseEntity.ok(records.stream().map(h -> toResponse(h)).toList());
    }

    private RoundHistoryResponse toResponse(RoundHistory h) {
        String displayName = null;
        if (!Boolean.TRUE.equals(h.getWinnerIsBot()) && h.getWinnerUserId() != null) {
            displayName = userRepository.findById(h.getWinnerUserId())
                .map(User::getUsername).orElse("Unknown");
        }
        return new RoundHistoryResponse(
            h.getRoomId(),
            displayName,
            h.getWinnerIsBot(),
            h.getTotalPool(),
            h.getPayout(),
            h.getParticipantCount(),
            h.getBotCount(),
            h.getRngRoll() != null ? h.getRngRoll().doubleValue() : null,
            h.getRngTotalWeight() != null ? h.getRngTotalWeight().doubleValue() : null,
            h.getFinishedAt()
        );
    }
}
