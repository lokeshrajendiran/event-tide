package com.eventide.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Centralizes all DLQ and topic configuration.
 *
 * Bound from application.yml under "eventide" prefix:
 *   eventide:
 *     topics:
 *       events: eventide.events
 *       dlq: eventide.dlq
 *       dlq-dead: eventide.dlq.dead
 *     dlq:
 *       max-retries: 3
 *       base-delay-ms: 5000
 *
 * Every service that needs topic names or retry config injects this
 * instead of hardcoding strings. Change once here, applies everywhere.
 */
@Component
@ConfigurationProperties(prefix = "eventide")
@Getter
@Setter
public class EventideProperties {

    private Topics topics = new Topics();
    private Dlq dlq = new Dlq();

    @Getter
    @Setter
    public static class Topics {
        private String events = "eventide.events";
        private String dlq = "eventide.dlq";
        private String dlqDead = "eventide.dlq.dead";
    }

    @Getter
    @Setter
    public static class Dlq {
        private int maxRetries = 3;
        private long baseDelayMs = 5000;
    }
}
