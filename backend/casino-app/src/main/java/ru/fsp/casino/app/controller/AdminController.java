package ru.fsp.casino.app.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.fsp.casino.app.dto.admin.AdminConfigDto;
import ru.fsp.casino.app.dto.admin.ConfigValidationResult;
import ru.fsp.casino.app.dto.room.RoomResponse;
import ru.fsp.casino.app.exception.RoomNotFoundException;
import ru.fsp.casino.domain.enums.RoomStatus;
import ru.fsp.casino.domain.model.AdminConfig;
import ru.fsp.casino.domain.model.Room;
import ru.fsp.casino.domain.repository.AdminConfigRepository;
import ru.fsp.casino.domain.repository.RoomRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminConfigRepository adminConfigRepository;
    private final RoomRepository roomRepository;
    private final RoomController roomController;

    @GetMapping("/config")
    public ResponseEntity<AdminConfigDto> getConfig() {
        AdminConfig cfg = adminConfigRepository.findById(1).orElseThrow();
        return ResponseEntity.ok(toDto(cfg));
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<RoomResponse>> getAllRooms() {
        return ResponseEntity.ok(roomRepository.findAll().stream()
            .map(roomController::toResponse).toList());
    }

    @PostMapping("/config/validate")
    public ResponseEntity<ConfigValidationResult> validateConfig(@RequestBody AdminConfigDto dto) {
        return ResponseEntity.ok(validate(dto));
    }

    @PostMapping("/config")
    public ResponseEntity<?> saveConfig(@RequestBody AdminConfigDto dto) {
        ConfigValidationResult result = validate(dto);
        if (!result.valid()) {
            return ResponseEntity.unprocessableEntity().body(result);
        }
        AdminConfig cfg = adminConfigRepository.findById(1).orElse(AdminConfig.builder().id(1).build());
        applyDto(cfg, dto);
        cfg.setUpdatedAt(Instant.now());
        adminConfigRepository.save(cfg);
        return ResponseEntity.ok(toDto(cfg));
    }

    @PutMapping("/rooms/{roomId}/config")
    public ResponseEntity<RoomResponse> updateRoomConfig(
            @PathVariable Long roomId,
            @RequestBody Map<String, Object> updates) {

        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));

        if (room.getStatus() != RoomStatus.WAITING) {
            return ResponseEntity.status(409).body(null);
        }

        if (updates.containsKey("maxSlots")) {
            room.setMaxSlots(((Number) updates.get("maxSlots")).intValue());
        }
        if (updates.containsKey("entryFee")) {
            room.setEntryFee(((Number) updates.get("entryFee")).longValue());
        }
        if (updates.containsKey("boostMultiplier")) {
            room.setBoostMultiplier(BigDecimal.valueOf(((Number) updates.get("boostMultiplier")).doubleValue()));
        }
        roomRepository.save(room);
        return ResponseEntity.ok(roomController.toResponse(room));
    }

    private ConfigValidationResult validate(AdminConfigDto dto) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (dto.defaultMaxSlots() != null && (dto.defaultMaxSlots() < 2 || dto.defaultMaxSlots() > 10)) {
            errors.add("maxSlots must be between 2 and 10");
        }
        if (dto.defaultEntryFee() != null && dto.defaultEntryFee() <= 0) {
            errors.add("entryFee must be positive");
        }
        if (dto.defaultPrizePoolPct() != null && (dto.defaultPrizePoolPct() < 0 || dto.defaultPrizePoolPct() > 100)) {
            errors.add("prizePoolPct must be between 0 and 100");
        }
        if (dto.defaultPrizePoolPct() != null && dto.defaultPrizePoolPct() < 60) {
            warnings.add("prizePoolPct ниже 60% — комната непривлекательна");
        }
        if (dto.defaultPrizePoolPct() != null && (100 - dto.defaultPrizePoolPct()) < 5) {
            warnings.add("Слишком низкая маржа оператора (< 5%)");
        }
        if (dto.defaultBoostMultiplier() != null && dto.defaultBoostMultiplier().doubleValue() > 4.0) {
            warnings.add("boostMultiplier > 4.0 может дисбалансировать игру");
        }
        if (dto.defaultBoostCost() != null && dto.defaultEntryFee() != null
                && dto.defaultBoostCost() > dto.defaultEntryFee() * 0.8) {
            warnings.add("boostCost > 80% от entryFee");
        }
        if (dto.waitingTimerSeconds() != null && dto.waitingTimerSeconds() < 10) {
            warnings.add("waitingTimerSeconds < 10 — слишком короткое ожидание");
        }

        double houseEdge = dto.defaultPrizePoolPct() != null
            ? (100 - dto.defaultPrizePoolPct()) / 100.0 : 0.20;
        double roi = houseEdge / (1 - houseEdge);

        return new ConfigValidationResult(errors.isEmpty(), errors, warnings, houseEdge, roi);
    }

    private AdminConfigDto toDto(AdminConfig cfg) {
        return new AdminConfigDto(
            cfg.getDefaultMaxSlots(), cfg.getDefaultEntryFee(), cfg.getDefaultPrizePoolPct(),
            cfg.getDefaultBoostEnabled(), cfg.getDefaultBoostCost(), cfg.getDefaultBoostMultiplier(),
            cfg.getWaitingTimerSeconds());
    }

    private void applyDto(AdminConfig cfg, AdminConfigDto dto) {
        if (dto.defaultMaxSlots() != null) cfg.setDefaultMaxSlots(dto.defaultMaxSlots());
        if (dto.defaultEntryFee() != null) cfg.setDefaultEntryFee(dto.defaultEntryFee());
        if (dto.defaultPrizePoolPct() != null) cfg.setDefaultPrizePoolPct(dto.defaultPrizePoolPct());
        if (dto.defaultBoostEnabled() != null) cfg.setDefaultBoostEnabled(dto.defaultBoostEnabled());
        if (dto.defaultBoostCost() != null) cfg.setDefaultBoostCost(dto.defaultBoostCost());
        if (dto.defaultBoostMultiplier() != null) cfg.setDefaultBoostMultiplier(dto.defaultBoostMultiplier());
        if (dto.waitingTimerSeconds() != null) cfg.setWaitingTimerSeconds(dto.waitingTimerSeconds());
    }
}
