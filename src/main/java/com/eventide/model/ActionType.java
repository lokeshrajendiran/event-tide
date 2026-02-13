package com.eventide.model;

/**
 * What kind of action to perform when a rule matches.
 * KAFKA   → publish a new event to a Kafka topic
 * WEBHOOK → send an HTTP POST to an external URL
 * HTTP    → make a generic HTTP call (GET/POST/PUT)
 */
public enum ActionType {
    KAFKA,
    WEBHOOK,
    HTTP
}
