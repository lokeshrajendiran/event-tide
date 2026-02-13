package com.eventide.service;

import com.eventide.dto.*;
import com.eventide.model.*;
import com.eventide.repository.WorkflowRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;

    @Transactional
    public WorkflowResponse create(WorkflowRequest request) {
        Workflow workflow = Workflow.builder()
                .name(request.getName())
                .description(request.getDescription())
                .eventType(request.getEventType())
                .source(request.getSource())
                .build();

        if (request.getRules() != null) {
            for (WorkflowRuleRequest ruleReq : request.getRules()) {
                WorkflowRule rule = WorkflowRule.builder()
                        .workflow(workflow)
                        .priority(ruleReq.getPriority())
                        .condition(ruleReq.getCondition())
                        .actionType(ruleReq.getActionType())
                        .actionConfig(ruleReq.getActionConfig())
                        .build();
                workflow.getRules().add(rule);
            }
        }

        Workflow saved = workflowRepository.save(workflow);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> listAll() {
        return workflowRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WorkflowResponse getById(UUID id) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Workflow not found: " + id));
        return toResponse(workflow);
    }

    @Transactional
    public WorkflowResponse update(UUID id, WorkflowRequest request) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Workflow not found: " + id));

        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setEventType(request.getEventType());
        workflow.setSource(request.getSource());

        // Replace all rules
        workflow.getRules().clear();
        if (request.getRules() != null) {
            for (WorkflowRuleRequest ruleReq : request.getRules()) {
                WorkflowRule rule = WorkflowRule.builder()
                        .workflow(workflow)
                        .priority(ruleReq.getPriority())
                        .condition(ruleReq.getCondition())
                        .actionType(ruleReq.getActionType())
                        .actionConfig(ruleReq.getActionConfig())
                        .build();
                workflow.getRules().add(rule);
            }
        }

        Workflow saved = workflowRepository.save(workflow);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!workflowRepository.existsById(id)) {
            throw new EntityNotFoundException("Workflow not found: " + id);
        }
        workflowRepository.deleteById(id);
    }

    @Transactional
    public WorkflowResponse toggleStatus(UUID id) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Workflow not found: " + id));

        workflow.setStatus(workflow.getStatus() == WorkflowStatus.ACTIVE
                ? WorkflowStatus.INACTIVE
                : WorkflowStatus.ACTIVE);

        return toResponse(workflowRepository.save(workflow));
    }

    // --- Mapping helpers ---

    private WorkflowResponse toResponse(Workflow w) {
        return WorkflowResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .description(w.getDescription())
                .eventType(w.getEventType())
                .source(w.getSource())
                .status(w.getStatus())
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .rules(w.getRules().stream().map(this::toRuleResponse).collect(Collectors.toList()))
                .build();
    }

    private WorkflowRuleResponse toRuleResponse(WorkflowRule r) {
        return WorkflowRuleResponse.builder()
                .id(r.getId())
                .priority(r.getPriority())
                .condition(r.getCondition())
                .actionType(r.getActionType())
                .actionConfig(r.getActionConfig())
                .build();
    }
}
