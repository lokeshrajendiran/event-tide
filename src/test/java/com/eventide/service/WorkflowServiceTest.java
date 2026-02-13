package com.eventide.service;

import com.eventide.dto.WorkflowRequest;
import com.eventide.dto.WorkflowResponse;
import com.eventide.dto.WorkflowRuleRequest;
import com.eventide.model.*;
import com.eventide.repository.WorkflowRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock private WorkflowRepository workflowRepository;

    @InjectMocks
    private WorkflowService workflowService;

    private WorkflowRequest sampleRequest;
    private Workflow sampleWorkflow;
    private UUID workflowId;

    @BeforeEach
    void setUp() {
        workflowId = UUID.randomUUID();

        sampleRequest = WorkflowRequest.builder()
                .name("Test Workflow")
                .description("A test workflow")
                .eventType("order.placed")
                .source("order-service")
                .rules(List.of(
                        WorkflowRuleRequest.builder()
                                .priority(1)
                                .condition("payload.total > 1000")
                                .actionType(ActionType.WEBHOOK)
                                .actionConfig("{\"url\": \"https://example.com\"}")
                                .build()
                ))
                .build();

        sampleWorkflow = Workflow.builder()
                .id(workflowId)
                .name("Test Workflow")
                .description("A test workflow")
                .eventType("order.placed")
                .source("order-service")
                .status(WorkflowStatus.ACTIVE)
                .rules(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("create() should save workflow and return response")
    void create_shouldSaveAndReturnResponse() {
        when(workflowRepository.save(any(Workflow.class))).thenReturn(sampleWorkflow);

        WorkflowResponse response = workflowService.create(sampleRequest);

        assertNotNull(response);
        assertEquals("Test Workflow", response.getName());
        assertEquals("order.placed", response.getEventType());
        verify(workflowRepository).save(any(Workflow.class));
    }

    @Test
    @DisplayName("getById() should return workflow when found")
    void getById_shouldReturnWorkflow() {
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(sampleWorkflow));

        WorkflowResponse response = workflowService.getById(workflowId);

        assertEquals(workflowId, response.getId());
        assertEquals("Test Workflow", response.getName());
    }

    @Test
    @DisplayName("getById() should throw when workflow not found")
    void getById_shouldThrowWhenNotFound() {
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> workflowService.getById(workflowId));
    }

    @Test
    @DisplayName("delete() should call repository deleteById")
    void delete_shouldCallRepository() {
        when(workflowRepository.existsById(workflowId)).thenReturn(true);

        workflowService.delete(workflowId);

        verify(workflowRepository).deleteById(workflowId);
    }

    @Test
    @DisplayName("delete() should throw when workflow not found")
    void delete_shouldThrowWhenNotFound() {
        when(workflowRepository.existsById(workflowId)).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> workflowService.delete(workflowId));
    }

    @Test
    @DisplayName("toggleStatus() should flip ACTIVE to INACTIVE")
    void toggleStatus_shouldFlip() {
        sampleWorkflow.setStatus(WorkflowStatus.ACTIVE);
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(sampleWorkflow));
        when(workflowRepository.save(any(Workflow.class))).thenReturn(sampleWorkflow);

        workflowService.toggleStatus(workflowId);

        assertEquals(WorkflowStatus.INACTIVE, sampleWorkflow.getStatus());
    }

    @Test
    @DisplayName("listAll() should return all workflows")
    void listAll_shouldReturnAll() {
        when(workflowRepository.findAll()).thenReturn(List.of(sampleWorkflow));

        List<WorkflowResponse> results = workflowService.listAll();

        assertEquals(1, results.size());
        assertEquals("Test Workflow", results.get(0).getName());
    }
}
