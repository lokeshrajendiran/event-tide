package com.eventide.service;

import com.eventide.config.EventideProperties;
import com.eventide.dto.IncomingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Dead-Letter Queue handler for failed event processing.
 *
 * When an action dispatch fails (webhook down, Kafka error, etc.),
 * the event is sent to the DLQ topic with:
 *   - The original event data
 *   - The error message
 *   - A retry count (incremented on each failure)
 *
 * This ensures no events are lost, and failed actions can be retried
 * or investigated later.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final EventideProperties properties;

    public void sendToDlq(IncomingEvent event, String errorMessage, int retryCount) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalEvent", event);
            dlqMessage.put("error", errorMessage);
            dlqMessage.put("retryCount", retryCount);
            dlqMessage.put("timestamp", System.currentTimeMillis());

            String message = objectMapper.writeValueAsString(dlqMessage);
            kafkaTemplate.send(properties.getTopics().getDlq(), event.getEventId(), message);
            log.info("Event sent to DLQ: eventId={}, retryCount={}, error={}",
                    event.getEventId(), retryCount, errorMessage);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send event to DLQ: {}", e.getMessage(), e);
        }
    }

    public void sendRawToDlq(String rawMessage, String errorMessage) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("rawMessage", rawMessage);
            dlqMessage.put("error", errorMessage);
            dlqMessage.put("retryCount", 0);
            dlqMessage.put("timestamp", System.currentTimeMillis());

            String message = objectMapper.writeValueAsString(dlqMessage);
            kafkaTemplate.send(properties.getTopics().getDlq(), message);
            log.info("Unparseable event sent to DLQ: error={}", errorMessage);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send raw event to DLQ: {}", e.getMessage(), e);
        }
    }

    /**
     * Sends an event to the permanent DLQ topic when all retries are exhausted
     * or the event is fundamentally unprocessable.
     * Events here require manual investigation — they will NOT be retried automatically.
     */
    public void sendToPermanentDlq(String originalDlqMessage, String reason) {
        try {
            Map<String, Object> permanentDlqMessage = new HashMap<>();
            permanentDlqMessage.put("originalDlqMessage", originalDlqMessage);
            permanentDlqMessage.put("reason", reason);
            permanentDlqMessage.put("timestamp", System.currentTimeMillis());

            String message = objectMapper.writeValueAsString(permanentDlqMessage);
            kafkaTemplate.send(properties.getTopics().getDlqDead(), message);
            log.error("CRITICAL: Event moved to permanent DLQ: reason={}", reason);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send to permanent DLQ: {}", e.getMessage(), e);
        }
    }

    public boolean isRetryable(int retryCount) {
        return retryCount < properties.getDlq().getMaxRetries();
    }
}
