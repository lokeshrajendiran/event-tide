package com.eventide.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeduplicationServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private DeduplicationService deduplicationService;

    @Test
    @DisplayName("New event (first time) should NOT be flagged as duplicate")
    void newEvent_shouldNotBeDuplicate() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // SET NX succeeds → key didn't exist → new event
        when(valueOps.setIfAbsent(eq("eventide:dedup:evt-001"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        assertFalse(deduplicationService.isDuplicate("evt-001"));
    }

    @Test
    @DisplayName("Repeated event should be flagged as duplicate")
    void repeatedEvent_shouldBeDuplicate() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // SET NX fails → key already exists → duplicate
        when(valueOps.setIfAbsent(eq("eventide:dedup:evt-001"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        assertTrue(deduplicationService.isDuplicate("evt-001"));
    }

    @Test
    @DisplayName("Null eventId should not be treated as duplicate")
    void nullEventId_shouldNotBeDuplicate() {
        assertFalse(deduplicationService.isDuplicate(null));
    }

    @Test
    @DisplayName("Blank eventId should not be treated as duplicate")
    void blankEventId_shouldNotBeDuplicate() {
        assertFalse(deduplicationService.isDuplicate("  "));
    }
}
