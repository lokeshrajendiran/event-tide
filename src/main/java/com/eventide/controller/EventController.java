package com.eventide.controller;

import com.eventide.dto.IncomingEvent;
import com.eventide.service.ChoreographyEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoint for submitting events directly (alternative to Kafka).
 * Useful for testing and for services that prefer HTTP over Kafka.
 *
 * POST /api/events
 * {
 *   "eventId": "evt-123",
 *   "eventType": "customer.created",
 *   "source": "user-service",
 *   "payload": {"name": "Lokesh", "plan": "enterprise"}
 * }
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final ChoreographyEngine engine;

    @PostMapping
    public ResponseEntity<Map<String, String>> submitEvent(@RequestBody IncomingEvent event) {
        engine.process(event);
        return ResponseEntity.accepted()
                .body(Map.of("status", "accepted", "eventId", event.getEventId()));
    }
}
