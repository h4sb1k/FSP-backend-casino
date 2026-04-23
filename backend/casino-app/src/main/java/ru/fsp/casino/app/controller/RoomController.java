package ru.fsp.casino.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.fsp.casino.app.dto.room.*;
import ru.fsp.casino.app.exception.RoomFullException;
import ru.fsp.casino.app.exception.RoomNotFoundException;
import ru.fsp.casino.app.websocket.RoomEventPublisher;
import ru.fsp.casino.domain.enums.RoomStatus;
import ru.fsp.casino.domain.enums.VipTier;
import ru.fsp.casino.domain.model.Room;
import ru.fsp.casino.domain.model.RoomParticipant;
import ru.fsp.casino.domain.model.User;
import ru.fsp.casino.domain.repository.AdminConfigRepository;
import ru.fsp.casino.domain.repository.RoomParticipantRepository;
import ru.fsp.casino.domain.repository.RoomRepository;
import ru.fsp.casino.domain.repository.UserRepository;
import ru.fsp.casino.app.redis.RateLimiter;
import ru.fsp.casino.app.redis.RoomCacheService;
import ru.fsp.casino.game.service.BalanceService;
import ru.fsp.casino.game.service.WinnerService;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final AdminConfigRepository adminConfigRepository;
    private final BalanceService balanceService;
    private final WinnerService winnerService;
    private final RoomEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final RateLimiter rateLimiter;
    private final RoomCacheService roomCache;

    @GetMapping
    public ResponseEntity<List<RoomResponse>> listRooms(
            @RequestParam(required = false) Long entryFeeMin,
            @RequestParam(required = false) Long entryFeeMax,
            @RequestParam(required = false) Integer seatsMin,
            @RequestParam(required = false) Integer seatsMax,
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String status) {

        // Use cache only for the default unfiltered query
        boolean isDefaultQuery = entryFeeMin == null && entryFeeMax == null
            && seatsMin == null && seatsMax == null && tier == null && status == null;
        if (isDefaultQuery) {
            var cached = roomCache.getRoomsList();
            if (cached.isPresent()) return ResponseEntity.ok(cached.get());
        }

        List<RoomStatus> statuses = status != null
            ? List.of(RoomStatus.valueOf(status.toUpperCase()))
            : List.of(RoomStatus.WAITING, RoomStatus.RUNNING);

        VipTier tierFilter = tier != null ? VipTier.valueOf(tier.toUpperCase()) : null;
        List<Room> rooms = roomRepository.findFiltered(statuses, tierFilter, entryFeeMin, entryFeeMax);

        if (seatsMin != null || seatsMax != null) {
            rooms = rooms.stream().filter(r -> {
                int filled = r.getParticipants().size();
                int available = r.getMaxSlots() - filled;
                if (seatsMin != null && available < seatsMin) return false;
                if (seatsMax != null && available > seatsMax) return false;
                return true;
            }).toList();
        }

        List<RoomResponse> result = rooms.stream().map(this::toResponse).toList();
        if (isDefaultQuery) roomCache.putRoomsList(result);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable Long roomId) {
        var cached = roomCache.getRoom(roomId);
        if (cached.isPresent()) return ResponseEntity.ok(cached.get());
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
        RoomResponse response = toResponse(room);
        roomCache.putRoom(roomId, response);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            Authentication auth) {

        Long userId = (Long) auth.getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();

        var config = adminConfigRepository.findById(1).orElse(null);
        int waitingSeconds = config != null ? config.getWaitingTimerSeconds() : 60;

        VipTier tier = request.tier() != null
            ? VipTier.valueOf(request.tier().toUpperCase())
            : VipTier.STANDARD;

        balanceService.reserve(userId, request.entryFee());

        Room room = Room.builder()
            .status(RoomStatus.WAITING)
            .tier(tier)
            .maxSlots(request.maxSlots())
            .entryFee(request.entryFee())
            .prizePoolPct(request.prizePoolPct() != null ? request.prizePoolPct() : 80)
            .boostEnabled(request.boostEnabled() != null ? request.boostEnabled() : true)
            .boostCost(request.boostCost())
            .boostMultiplier(request.boostMultiplier() != null
                ? BigDecimal.valueOf(request.boostMultiplier()) : BigDecimal.valueOf(2.0))
            .creator(user)
            .timerStartedAt(Instant.now())
            .build();
        room = roomRepository.save(room);

        RoomParticipant creator = RoomParticipant.builder()
            .room(room).user(user).isBot(false).boosted(false).build();
        participantRepository.save(creator);

        redisTemplate.opsForValue().set(
            "room:timer:" + room.getId(),
            String.valueOf(waitingSeconds),
            Duration.ofSeconds(waitingSeconds));

        roomCache.evictRoomsList();
        eventPublisher.publishRoomsListUpdate(null);

        RoomResponse response = toResponse(roomRepository.findById(room.getId()).orElseThrow());
        roomCache.putRoom(room.getId(), response);
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<RoomResponse> joinRoom(@PathVariable Long roomId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (!rateLimiter.allow("join:" + userId, 5, Duration.ofSeconds(30))) {
            throw new IllegalStateException("Too many join attempts, please slow down");
        }
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new RoomFullException(roomId);
        }

        List<RoomParticipant> participants = participantRepository.findByRoom_Id(roomId);
        if (participants.size() >= room.getMaxSlots()) {
            throw new RoomFullException(roomId);
        }

        if (participantRepository.existsByRoom_IdAndUser_Id(roomId, userId)) {
            throw new IllegalStateException("Already in this room");
        }

        var activeRooms = participantRepository.findActiveByUserId(
            userId, List.of(RoomStatus.WAITING, RoomStatus.RUNNING));
        if (!activeRooms.isEmpty()) {
            throw new IllegalStateException("Already in another active room");
        }

        balanceService.reserve(userId, room.getEntryFee());

        User user = userRepository.findById(userId).orElseThrow();
        RoomParticipant participant = RoomParticipant.builder()
            .room(room).user(user).isBot(false).boosted(false).build();
        participantRepository.save(participant);

        eventPublisher.publishParticipantUpdate(roomId, participantRepository.findByRoom_Id(roomId));

        roomCache.evictRoom(roomId);
        roomCache.evictRoomsList();
        return ResponseEntity.ok(toResponse(roomRepository.findById(roomId).orElseThrow()));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Map<String, String>> leaveRoom(@PathVariable Long roomId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new IllegalStateException("Can only leave WAITING rooms");
        }

        RoomParticipant participant = participantRepository.findByRoom_IdAndUser_Id(roomId, userId)
            .orElseThrow(() -> new IllegalStateException("Not in this room"));

        balanceService.release(userId, room.getEntryFee());
        participantRepository.delete(participant);

        eventPublisher.publishParticipantUpdate(roomId, participantRepository.findByRoom_Id(roomId));

        roomCache.evictRoom(roomId);
        roomCache.evictRoomsList();
        return ResponseEntity.ok(Map.of("message", "Left room successfully"));
    }

    @PostMapping("/{roomId}/boost")
    public ResponseEntity<BoostResponse> boost(@PathVariable Long roomId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (!rateLimiter.allow("boost:" + userId, 3, Duration.ofSeconds(60))) {
            throw new IllegalStateException("Too many boost attempts, please slow down");
        }
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (!Boolean.TRUE.equals(room.getBoostEnabled())) {
            throw new IllegalStateException("Boost is disabled for this room");
        }

        RoomParticipant participant = participantRepository.findByRoom_IdAndUser_Id(roomId, userId)
            .orElseThrow(() -> new IllegalStateException("Not in this room"));

        if (Boolean.TRUE.equals(participant.getBoosted())) {
            throw new IllegalStateException("Boost already active");
        }

        Long boostCost = room.getBoostCost();
        if (boostCost == null || boostCost <= 0) {
            throw new IllegalStateException("Boost cost not configured");
        }

        balanceService.deduct(userId, boostCost);

        participant.setBoosted(true);
        participantRepository.save(participant);

        roomCache.evictRoom(roomId);
        roomCache.evictRoomsList();

        double prob = winnerService.calculateWinProbability(room, true);

        return ResponseEntity.ok(new BoostResponse(prob, room.getBoostMultiplier().doubleValue(), boostCost));

    }

    public RoomResponse toResponse(Room room) {
        List<RoomParticipant> participants = participantRepository.findByRoom_Id(room.getId());
        List<ParticipantResponse> participantDtos = participants.stream()
            .map(p -> new ParticipantResponse(
                p.getId(),
                p.getUser() != null ? p.getUser().getId() : null,
                p.getUser() != null ? p.getUser().getUsername() : null,
                p.getIsBot(),
                p.getBotName(),
                p.getBoosted()))
            .toList();

        return new RoomResponse(
            room.getId(),
            room.getStatus().name(),
            room.getTier() != null ? room.getTier().name() : null,
            room.getMaxSlots(),
            participants.size(),
            room.getEntryFee(),
            room.getPrizePoolPct(),
            room.getBoostEnabled(),
            room.getBoostCost(),
            room.getBoostMultiplier() != null ? room.getBoostMultiplier().doubleValue() : null,
            participantDtos,
            room.getTimerStartedAt(),
            room.getCreatedAt()
        );
    }
}
