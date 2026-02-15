package com.eventide.service;

import com.eventide.config.EventideProperties;
import com.eventide.dto.IncomingEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes failed events from the Dead Letter Queue and retries them.
 *
 * FLOW:
 *   eventide.dlq → DlqRetryConsumer reads the message
 *                       ↓
 *               Parse DLQ envelope (originalEvent, retryCount, error)
 *                       ↓
 *               Is retryCount < MAX_RETRIES?
 *          ┌─── YES ────┴──── NO ────┐
 *          ↓                          ↓
 *    Wait (exponential backoff)    Send to eventide.dlq.dead
 *    Clear dedup key               Log CRITICAL alert
 *    Re-publish to eventide.events
 *
 * BACKOFF STRATEGY (default, configurable via application.yml):
 *   Retry 1 → 5 seconds
 *   Retry 2 → 25 seconds
 *   Retry 3 → exceeds MAX_RETRIES → permanent DLQ
 *
 * Uses a separate consumer group "eventide-dlq-processor" so retries
 * don't interfere with the main event processing pipeline.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DlqRetryConsumer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DeduplicationService deduplicationService;
    private final DeadLetterQueueService deadLetterQueueService;
    private final ObjectMapper objectMapper;
    private final EventideProperties properties;

    @KafkaListener(topics = "${eventide.topics.dlq:eventide.dlq}", groupId = "eventide-dlq-processor")
    public void onDlqMessage(String message) {
        try {
            Map<String, Object> dlqEnvelope = objectMapper.readValue(
                    message, new TypeReference<>() {});

            int retryCount = (int) dlqEnvelope.getOrDefault("retryCount", 0);

            // Raw messages (deserialization failures) can't be retried — park immediately
            if (dlqEnvelope.containsKey("rawMessage")) {
                log.warn("Unparseable event in DLQ, moving to permanent DLQ: {}",
                        dlqEnvelope.get("error"));
                deadLetterQueueService.sendToPermanentDlq(message,
                        "Unparseable event — cannot retry");
                return;
            }

            if (!deadLetterQueueService.isRetryable(retryCount)) {
                log.error("CRITICAL: Event exhausted all retries (count={}), moving to permanent DLQ",
                        retryCount);
                deadLetterQueueService.sendToPermanentDlq(message,
                        "Max retries exceeded: " + retryCount);
                return;
            }

            // Extract original event
            Object originalEventObj = dlqEnvelope.get("originalEvent");
            if (originalEventObj == null) {
                log.error("DLQ message missing originalEvent field, parking permanently");
                deadLetterQueueService.sendToPermanentDlq(message,
                        "Missing originalEvent field");
                return;
            }

            IncomingEvent originalEvent = objectMapper.convertValue(
                    originalEventObj, IncomingEvent.class);

            // Exponential backoff
            long delayMs = calculateBackoff(retryCount);
            log.info("Retrying DLQ event: eventId={}, attempt={}, backoff={}ms",
                    originalEvent.getEventId(), retryCount + 1, delayMs);

            Thread.sleep(delayMs);

            // Clear dedup so the event won't be blocked on re-processing
            deduplicationService.clearDedup(originalEvent.getEventId());

            // Stamp the retry count into the event payload so downstream
            // can track it, and the next DLQ entry uses the incremented count
            originalEvent.getPayload().put("_retryCount", retryCount + 1);

            String eventsTopic = properties.getTopics().getEvents();
            String republishMessage = objectMapper.writeValueAsString(originalEvent);
            kafkaTemplate.send(eventsTopic, originalEvent.getEventId(), republishMessage);

            log.info("Re-published DLQ event to {}: eventId={}, attempt={}",
                    eventsTopic, originalEvent.getEventId(), retryCount + 1);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("DLQ retry interrupted: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process DLQ message: {}", e.getMessage(), e);
        }
    }

    long calculateBackoff(int retryCount) {
        long baseDelay = properties.getDlq().getBaseDelayMs();
        return baseDelay * (long) Math.pow(5, retryCount);
    }
}
