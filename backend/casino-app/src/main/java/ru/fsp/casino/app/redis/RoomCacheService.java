package ru.fsp.casino.app.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import ru.fsp.casino.app.dto.room.RoomResponse;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomCacheService {

    private static final String ROOMS_LIST_KEY = "cache:rooms:active";
    private static final String ROOM_KEY_PREFIX = "cache:room:";
    private static final Duration ROOMS_LIST_TTL = Duration.ofSeconds(3);
    private static final Duration ROOM_TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<List<RoomResponse>> getRoomsList() {
        try {
            String json = redisTemplate.opsForValue().get(ROOMS_LIST_KEY);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, new TypeReference<>() {}));
        } catch (Exception e) {
            log.warn("Cache read failed for rooms list: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void putRoomsList(List<RoomResponse> rooms) {
        try {
            redisTemplate.opsForValue().set(ROOMS_LIST_KEY,
                objectMapper.writeValueAsString(rooms), ROOMS_LIST_TTL);
        } catch (Exception e) {
            log.warn("Cache write failed for rooms list: {}", e.getMessage());
        }
    }

    public void evictRoomsList() {
        redisTemplate.delete(ROOMS_LIST_KEY);
    }

    public Optional<RoomResponse> getRoom(Long roomId) {
        try {
            String json = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, RoomResponse.class));
        } catch (Exception e) {
            log.warn("Cache read failed for room {}: {}", roomId, e.getMessage());
            return Optional.empty();
        }
    }

    public void putRoom(Long roomId, RoomResponse room) {
        try {
            redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId,
                objectMapper.writeValueAsString(room), ROOM_TTL);
        } catch (Exception e) {
            log.warn("Cache write failed for room {}: {}", roomId, e.getMessage());
        }
    }

    public void evictRoom(Long roomId) {
        redisTemplate.delete(ROOM_KEY_PREFIX + roomId);
    }
}
