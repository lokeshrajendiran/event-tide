package com.eventide.service;

import com.eventide.dto.IncomingEvent;
import com.eventide.model.*;
import com.eventide.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ChoreographyEngine — the core processing pipeline.
 *
 * We mock all dependencies (DB, Redis, Kafka) because we're testing
 * the ENGINE LOGIC, not the infrastructure. Each test verifies a
 * specific behavior: matching, condition evaluation, dispatch, dedup, DLQ.
 */
@ExtendWith(MockitoExtension.class)
class ChoreographyEngineTest {

    @Mock private WorkflowRepository workflowRepository;
    @Mock private ConditionEvaluator conditionEvaluator;
    @Mock private ActionDispatcher actionDispatcher;
    @Mock private DeduplicationService deduplicationService;
    @Mock private DeadLetterQueueService dlqService;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ChoreographyEngine engine;

    private IncomingEvent sampleEvent;
    private Workflow sampleWorkflow;

    @BeforeEach
    void setUp() {
        sampleEvent = IncomingEvent.builder()
                .eventId("evt-001")
                .eventType("customer.created")
                .source("user-service")
                .payload(Map.of("plan", "enterprise", "name", "Lokesh"))
                .build();

        WorkflowRule rule = WorkflowRule.builder()
                .id(UUID.randomUUID())
                .priority(1)
                .condition("payload.plan == 'enterprise'")
                .actionType(ActionType.KAFKA)
                .actionConfig("{\"topic\": \"onboarding\"}")
                .build();

        sampleWorkflow = Workflow.builder()
                .id(UUID.randomUUID())
                .name("Customer Onboarding")
                .eventType("customer.created")
                .source("user-service")
                .status(WorkflowStatus.ACTIVE)
                .rules(new ArrayList<>(List.of(rule)))
                .build();
    }

    @Test
    @DisplayName("Should process event when workflow matches and condition is true")
    void matchingWorkflowAndCondition_shouldDispatchAction() {
        when(deduplicationService.isDuplicate("evt-001")).thenReturn(false);
        when(workflowRepository.findByEventTypeAndSourceAndStatus(
                "customer.created", "user-service", WorkflowStatus.ACTIVE))
                .thenReturn(Optional.of(sampleWorkflow));
        when(conditionEvaluator.evaluate(anyString(), anyMap())).thenReturn(true);

        engine.process(sampleEvent);

        verify(actionDispatcher).dispatch(eq(ActionType.KAFKA), anyMap(), eq(sampleEvent));
    }

    @Test
    @DisplayName("Should skip processing when event is a duplicate")
    void duplicateEvent_shouldSkip() {
        when(deduplicationService.isDuplicate("evt-001")).thenReturn(true);

        engine.process(sampleEvent);

        // Should never even look up workflows
        verify(workflowRepository, never()).findByEventTypeAndSourceAndStatus(any(), any(), any());
        verify(actionDispatcher, never()).dispatch(any(), anyMap(), any());
    }

    @Test
    @DisplayName("Should do nothing when no workflow matches the event")
    void noMatchingWorkflow_shouldDoNothing() {
        when(deduplicationService.isDuplicate("evt-001")).thenReturn(false);
        when(workflowRepository.findByEventTypeAndSourceAndStatus(
                "customer.created", "user-service", WorkflowStatus.ACTIVE))
                .thenReturn(Optional.empty());

        engine.process(sampleEvent);

        verify(actionDispatcher, never()).dispatch(any(), anyMap(), any());
    }

    @Test
    @DisplayName("Should skip rule when condition evaluates to false")
    void conditionFalse_shouldNotDispatch() {
        when(deduplicationService.isDuplicate("evt-001")).thenReturn(false);
        when(workflowRepository.findByEventTypeAndSourceAndStatus(
                "customer.created", "user-service", WorkflowStatus.ACTIVE))
                .thenReturn(Optional.of(sampleWorkflow));
        when(conditionEvaluator.evaluate(anyString(), anyMap())).thenReturn(false);

        engine.process(sampleEvent);

        verify(actionDispatcher, never()).dispatch(any(), anyMap(), any());
    }

    @Test
    @DisplayName("Should send to DLQ when action dispatch fails")
    void dispatchFailure_shouldSendToDlq() {
        when(deduplicationService.isDuplicate("evt-001")).thenReturn(false);
        when(workflowRepository.findByEventTypeAndSourceAndStatus(
                "customer.created", "user-service", WorkflowStatus.ACTIVE))
                .thenReturn(Optional.of(sampleWorkflow));
        when(conditionEvaluator.evaluate(anyString(), anyMap())).thenReturn(true);
        doThrow(new RuntimeException("Kafka down"))
                .when(actionDispatcher).dispatch(any(), anyMap(), any());

        engine.process(sampleEvent);

        verify(dlqService).sendToDlq(eq(sampleEvent), contains("Kafka down"), eq(0));
    }

    @Test
    @DisplayName("Should evaluate multiple rules in priority order")
    void multipleRules_shouldEvaluateAll() {
        WorkflowRule rule2 = WorkflowRule.builder()
                .id(UUID.randomUUID())
                .priority(2)
                .condition("payload.name == 'Lokesh'")
                .actionType(ActionType.WEBHOOK)
                .actionConfig("{\"url\": \"https://example.com\"}")
                .build();
        sampleWorkflow.getRules().add(rule2);

        when(deduplicationService.isDuplicate("evt-001")).thenReturn(false);
        when(workflowRepository.findByEventTypeAndSourceAndStatus(
                "customer.created", "user-service", WorkflowStatus.ACTIVE))
                .thenReturn(Optional.of(sampleWorkflow));
        when(conditionEvaluator.evaluate(anyString(), anyMap())).thenReturn(true);

        engine.process(sampleEvent);

        // Both rules matched → both actions dispatched
        verify(actionDispatcher, times(2)).dispatch(any(), anyMap(), eq(sampleEvent));
    }
}
