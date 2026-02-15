package com.eventide.service;

import com.eventide.config.EventideProperties;
import com.eventide.dto.IncomingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DlqRetryConsumer — the DLQ auto-retry mechanism.
 *
 * Verifies:
 *   - Retryable events are re-published to eventide.events after backoff
 *   - Max-retried events are sent to permanent DLQ
 *   - Unparseable (raw) events are immediately parked
 *   - Malformed DLQ messages are handled gracefully
 *   - Exponential backoff calculation is correct
 *   - Dedup keys are cleared before retry
 */
@ExtendWith(MockitoExtension.class)
class DlqRetryConsumerTest {

    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private DeduplicationService deduplicationService;
    @Mock private DeadLetterQueueService deadLetterQueueService;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    private EventideProperties properties;
    private DlqRetryConsumer consumer;

    private String retryableDlqMessage;
    private String maxRetriedDlqMessage;
    private String rawDlqMessage;

    @BeforeEach
    void setUp() throws Exception {
        properties = new EventideProperties();
        // defaults: events=eventide.events, dlq=eventide.dlq, dlqDead=eventide.dlq.dead
        // defaults: maxRetries=3, baseDelayMs=5000
        consumer = new DlqRetryConsumer(kafkaTemplate, deduplicationService,
                deadLetterQueueService, objectMapper, properties);

        // A retryable DLQ message (retryCount=0, first failure)
        IncomingEvent event = IncomingEvent.builder()
                .eventId("evt-retry-001")
                .eventType("order.placed")
                .source("order-service")
                .payload(new HashMap<>(Map.of("amount", 100)))
                .build();

        Map<String, Object> retryableEnvelope = new HashMap<>();
        retryableEnvelope.put("originalEvent", event);
        retryableEnvelope.put("error", "Webhook timeout");
        retryableEnvelope.put("retryCount", 0);
        retryableEnvelope.put("timestamp", System.currentTimeMillis());
        retryableDlqMessage = objectMapper.writeValueAsString(retryableEnvelope);

        // A max-retried DLQ message (retryCount=3, exhausted)
        Map<String, Object> maxRetriedEnvelope = new HashMap<>();
        maxRetriedEnvelope.put("originalEvent", event);
        maxRetriedEnvelope.put("error", "Webhook still down");
        maxRetriedEnvelope.put("retryCount", 3);
        maxRetriedEnvelope.put("timestamp", System.currentTimeMillis());
        maxRetriedDlqMessage = objectMapper.writeValueAsString(maxRetriedEnvelope);

        // A raw (unparseable) DLQ message — deserialization failure
        Map<String, Object> rawEnvelope = new HashMap<>();
        rawEnvelope.put("rawMessage", "{bad json}");
        rawEnvelope.put("error", "Unrecognized token");
        rawEnvelope.put("retryCount", 0);
        rawEnvelope.put("timestamp", System.currentTimeMillis());
        rawDlqMessage = objectMapper.writeValueAsString(rawEnvelope);
    }

    @Test
    @DisplayName("Should retry event and re-publish to eventide.events")
    void retryableEvent_shouldRepublish() {
        when(deadLetterQueueService.isRetryable(0)).thenReturn(true);

        consumer.onDlqMessage(retryableDlqMessage);

        // Should clear dedup before re-publishing
        verify(deduplicationService).clearDedup("evt-retry-001");
        // Should re-publish to the main events topic
        verify(kafkaTemplate).send(eq("eventide.events"), eq("evt-retry-001"), anyString());
        // Should NOT send to permanent DLQ
        verify(deadLetterQueueService, never()).sendToPermanentDlq(anyString(), anyString());
    }

    @Test
    @DisplayName("Should send to permanent DLQ when max retries exhausted")
    void maxRetries_shouldParkPermanently() {
        when(deadLetterQueueService.isRetryable(3)).thenReturn(false);

        consumer.onDlqMessage(maxRetriedDlqMessage);

        verify(deadLetterQueueService).sendToPermanentDlq(eq(maxRetriedDlqMessage),
                contains("Max retries exceeded"));
        // Should NOT re-publish
        verify(kafkaTemplate, never()).send(eq("eventide.events"), anyString(), anyString());
        // Should NOT touch dedup
        verify(deduplicationService, never()).clearDedup(anyString());
    }

    @Test
    @DisplayName("Should immediately park unparseable (raw) events")
    void rawEvent_shouldParkImmediately() {
        consumer.onDlqMessage(rawDlqMessage);

        verify(deadLetterQueueService).sendToPermanentDlq(eq(rawDlqMessage),
                contains("Unparseable"));
        verify(kafkaTemplate, never()).send(eq("eventide.events"), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle completely malformed DLQ message gracefully")
    void malformedMessage_shouldNotThrow() {
        // Should not throw — just log error and move on
        consumer.onDlqMessage("not valid json at all");

        // No crash, no re-publish, no permanent DLQ (can't even parse the envelope)
        verify(kafkaTemplate, never()).send(eq("eventide.events"), anyString(), anyString());
    }

    @Test
    @DisplayName("Should park event when originalEvent field is missing")
    void missingOriginalEvent_shouldPark() throws Exception {
        Map<String, Object> brokenEnvelope = new HashMap<>();
        brokenEnvelope.put("error", "Some error");
        brokenEnvelope.put("retryCount", 0);
        String brokenMessage = objectMapper.writeValueAsString(brokenEnvelope);

        when(deadLetterQueueService.isRetryable(0)).thenReturn(true);

        consumer.onDlqMessage(brokenMessage);

        verify(deadLetterQueueService).sendToPermanentDlq(eq(brokenMessage),
                contains("Missing originalEvent"));
        verify(kafkaTemplate, never()).send(eq("eventide.events"), anyString(), anyString());
    }

    @Test
    @DisplayName("Exponential backoff should calculate correctly")
    void backoff_shouldBeExponential() {
        assertEquals(5_000, consumer.calculateBackoff(0));   // 5s × 5^0
        assertEquals(25_000, consumer.calculateBackoff(1));  // 5s × 5^1
        assertEquals(125_000, consumer.calculateBackoff(2)); // 5s × 5^2
    }
}
