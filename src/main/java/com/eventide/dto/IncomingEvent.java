package com.eventide.dto;

import lombok.*;
import java.util.Map;

/**
 * Represents an incoming event from any external service.
 *
 * Example JSON:
 * {
 *   "eventId": "evt-abc-123",
 *   "eventType": "customer.created",
 *   "source": "user-service",
 *   "payload": {
 *     "name": "Lokesh",
 *     "plan": "enterprise"
 *   }
 * }
 *
 * - eventId:   Unique identifier for dedup (prevent processing same event twice)
 * - eventType: Used to match against Workflow.eventType
 * - source:    Used to match against Workflow.source
 * - payload:   Arbitrary key-value data that conditions are evaluated against
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IncomingEvent {

    private String eventId;
    private String eventType;
    private String source;
    private Map<String, Object> payload;
}
