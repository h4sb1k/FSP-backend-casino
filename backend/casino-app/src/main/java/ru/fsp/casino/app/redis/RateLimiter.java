package ru.fsp.casino.app.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    /**
     * Returns true if the action is allowed, false if rate limit exceeded.
     * Uses a fixed window counter per key.
     */
    public boolean allow(String key, int maxRequests, Duration window) {
        String redisKey = "ratelimit:" + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == 1) {
            redisTemplate.expire(redisKey, window);
        }
        return count <= maxRequests;
    }
}
