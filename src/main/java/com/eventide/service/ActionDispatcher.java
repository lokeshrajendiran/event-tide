package com.eventide.service;

import com.eventide.dto.IncomingEvent;
import com.eventide.model.ActionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.Map;

/**
 * Executes actions when a workflow rule matches.
 *
 * Supports 3 action types:
 *   KAFKA   → Publishes a message to a target Kafka topic
 *   WEBHOOK → Sends an HTTP POST with the event payload to a URL
 *   HTTP    → Makes a configurable HTTP call (method, url, body, headers)
 *
 * Each action reads its configuration from the rule's actionConfig JSON:
 *   KAFKA:   {"topic": "onboarding", "key": "customer-123"}
 *   WEBHOOK: {"url": "https://example.com/hook"}
 *   HTTP:    {"url": "https://api.example.com", "method": "POST"}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActionDispatcher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public void dispatch(ActionType actionType, Map<String, Object> config, IncomingEvent event) {
        switch (actionType) {
            case KAFKA -> dispatchKafka(config, event);
            case WEBHOOK -> dispatchWebhook(config, event);
            case HTTP -> dispatchHttp(config, event);
        }
    }

    /**
     * Publishes the event payload to a target Kafka topic.
     * Config: {"topic": "target-topic", "key": "optional-partition-key"}
     */
    private void dispatchKafka(Map<String, Object> config, IncomingEvent event) {
        String topic = (String) config.get("topic");
        String key = (String) config.getOrDefault("key", event.getEventId());

        try {
            String message = objectMapper.writeValueAsString(event.getPayload());
            kafkaTemplate.send(topic, key, message);
            log.info("Dispatched KAFKA action → topic={}, key={}", topic, key);
        } catch (Exception e) {
            log.error("KAFKA dispatch failed: {}", e.getMessage(), e);
            throw new RuntimeException("Kafka dispatch failed", e);
        }
    }

    /**
     * Sends an HTTP POST with the event payload to a webhook URL.
     * Config: {"url": "https://example.com/webhook"}
     */
    private void dispatchWebhook(Map<String, Object> config, IncomingEvent event) {
        String url = (String) config.get("url");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = objectMapper.writeValueAsString(event);
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Dispatched WEBHOOK → url={}, status={}", url, response.getStatusCode());
        } catch (Exception e) {
            log.error("WEBHOOK dispatch failed: {}", e.getMessage(), e);
            throw new RuntimeException("Webhook dispatch failed", e);
        }
    }

    /**
     * Makes a configurable HTTP call.
     * Config: {"url": "...", "method": "POST", "headers": {"Auth": "Bearer ..."}}
     */
    private void dispatchHttp(Map<String, Object> config, IncomingEvent event) {
        String url = (String) config.get("url");
        String method = (String) config.getOrDefault("method", "POST");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Add custom headers if provided
            Object customHeaders = config.get("headers");
            if (customHeaders instanceof Map) {
                ((Map<?, ?>) customHeaders).forEach((k, v) ->
                        headers.set(k.toString(), v.toString()));
            }

            String body = objectMapper.writeValueAsString(event.getPayload());
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.valueOf(method.toUpperCase()), request, String.class);
            log.info("Dispatched HTTP {} → url={}, status={}", method, url, response.getStatusCode());
        } catch (Exception e) {
            log.error("HTTP dispatch failed: {}", e.getMessage(), e);
            throw new RuntimeException("HTTP dispatch failed", e);
        }
    }
}
