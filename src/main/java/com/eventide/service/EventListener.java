package com.eventide.service;

import com.eventide.dto.IncomingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens for incoming events on the "eventide.events" topic.
 *
 * FLOW:
 *   External service publishes event → Kafka topic "eventide.events"
 *                                          ↓
 *                                    EventListener reads it
 *                                          ↓
 *                                    Deserializes JSON → IncomingEvent
 *                                          ↓
 *                                    Passes to ChoreographyEngine.process()
 *
 * The consumer group "eventide-engine" ensures that in a multi-instance
 * deployment, each event is processed by exactly one instance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventListener {

    private final ChoreographyEngine engine;
    private final ObjectMapper objectMapper;
    private final DeadLetterQueueService deadLetterQueueService;

    @KafkaListener(topics = "eventide.events", groupId = "eventide-engine")
    public void onEvent(String message) {
        try {
            IncomingEvent event = objectMapper.readValue(message, IncomingEvent.class);
            engine.process(event);
        } catch (Exception e) {
            log.error("Failed to process event: {}", e.getMessage(), e);
            deadLetterQueueService.sendRawToDlq(message, e.getMessage());
        }
    }
}
