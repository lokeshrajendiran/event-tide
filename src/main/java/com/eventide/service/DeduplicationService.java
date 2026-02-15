package com.eventide.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Prevents duplicate event processing using Redis.
 *
 * HOW IT WORKS:
 *   1. When an event arrives, call isDuplicate(eventId)
 *   2. This tries to SET a key "eventide:dedup:{eventId}" in Redis with NX flag
 *      (NX = only set if the key does NOT exist)
 *   3. If the SET succeeds → this is a NEW event, return false (not duplicate)
 *   4. If the SET fails   → key already exists, return true (duplicate)
 *
 * The key auto-expires after 24 hours (TTL), so Redis doesn't grow forever.
 *
 * WHY REDIS?
 *   - O(1) lookup time
 *   - Atomic SET NX operation (thread-safe, even across multiple instances)
 *   - TTL handles cleanup automatically
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeduplicationService {

    private final StringRedisTemplate redisTemplate;

    private static final String DEDUP_PREFIX = "eventide:dedup:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    /**
     * Check if an event has already been processed.
     * Returns true if duplicate (already seen), false if new.
     */
    public boolean isDuplicate(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false; // No eventId = can't dedup, process anyway
        }

        String key = DEDUP_PREFIX + eventId;

        // SET NX = "set if not exists" — atomic check-and-set
        Boolean wasSet = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", DEDUP_TTL);

        if (Boolean.TRUE.equals(wasSet)) {
            // Key didn't exist → first time seeing this event
            return false;
        } else {
            // Key already existed → duplicate
            log.warn("Duplicate event detected: {}", eventId);
            return true;
        }
    }

    /**
     * Clears the dedup key for an event so it can be re-processed.
     * Used by the DLQ retry consumer — without this, retried events
     * would be blocked by the dedup check.
     */
    public void clearDedup(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        String key = DEDUP_PREFIX + eventId;
        redisTemplate.delete(key);
        log.info("Cleared dedup key for retry: eventId={}", eventId);
    }
}
