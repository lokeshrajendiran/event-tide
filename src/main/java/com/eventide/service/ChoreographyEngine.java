package com.eventide.service;

import com.eventide.dto.IncomingEvent;
import com.eventide.model.Workflow;
import com.eventide.model.WorkflowRule;
import com.eventide.model.WorkflowStatus;
import com.eventide.repository.WorkflowRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The core choreography engine.
 *
 * FLOW:
 *   1. Receive an event (eventType + source + payload)
 *   2. Look up: is there an ACTIVE workflow listening for this event?
 *   3. If yes → iterate through the workflow's rules (sorted by priority)
 *   4. For each rule → evaluate the condition against the event payload
 *   5. If condition matches → dispatch the action (Kafka / Webhook / HTTP)
 *
 * This is the heart of the system. Everything else exists to feed events
 * into this engine and execute the actions it decides on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChoreographyEngine {

    private final WorkflowRepository workflowRepository;
    private final ConditionEvaluator conditionEvaluator;
    private final ActionDispatcher actionDispatcher;
    private final DeduplicationService deduplicationService;
    private final DeadLetterQueueService dlqService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public void process(IncomingEvent event) {
        log.info("Processing event: type={}, source={}, id={}",
                event.getEventType(), event.getSource(), event.getEventId());

        // Step 0: Dedup check — skip if we've already processed this event
        if (deduplicationService.isDuplicate(event.getEventId())) {
            log.info("Skipping duplicate event: {}", event.getEventId());
            return;
        }

        // Step 1: Find matching workflow
        Optional<Workflow> match = workflowRepository
                .findByEventTypeAndSourceAndStatus(
                        event.getEventType(),
                        event.getSource(),
                        WorkflowStatus.ACTIVE);

        if (match.isEmpty()) {
            log.debug("No active workflow found for event: type={}, source={}",
                    event.getEventType(), event.getSource());
            return;
        }

        Workflow workflow = match.get();
        log.info("Matched workflow: '{}' (id={})", workflow.getName(), workflow.getId());

        // Step 2: Evaluate rules in priority order
        List<WorkflowRule> rules = workflow.getRules();

        for (WorkflowRule rule : rules) {
            boolean matches = conditionEvaluator.evaluate(
                    rule.getCondition(), event.getPayload());

            if (matches) {
                log.info("Rule matched: priority={}, condition='{}', action={}",
                        rule.getPriority(), rule.getCondition(), rule.getActionType());

                // Step 3: Dispatch the action
                try {
                    Map<String, Object> actionConfig = objectMapper.readValue(
                            rule.getActionConfig(),
                            new TypeReference<Map<String, Object>>() {});

                    actionDispatcher.dispatch(rule.getActionType(), actionConfig, event);
                } catch (Exception e) {
                    log.error("Failed to dispatch action for rule {}: {}",
                            rule.getId(), e.getMessage(), e);
                    // Carry forward retryCount if this is a retried event
                    int retryCount = 0;
                    Object rc = event.getPayload().get("_retryCount");
                    if (rc instanceof Number) {
                        retryCount = ((Number) rc).intValue();
                    }
                    dlqService.sendToDlq(event, e.getMessage(), retryCount);
                }
            } else {
                log.debug("Rule skipped: priority={}, condition='{}'",
                        rule.getPriority(), rule.getCondition());
            }
        }
    }
}
