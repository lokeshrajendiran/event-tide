package com.eventide.dto;

import com.eventide.model.WorkflowStatus;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkflowResponse {
    private UUID id;
    private String name;
    private String description;
    private String eventType;
    private String source;
    private WorkflowStatus status;
    private List<WorkflowRuleResponse> rules;
    private Instant createdAt;
    private Instant updatedAt;
}
