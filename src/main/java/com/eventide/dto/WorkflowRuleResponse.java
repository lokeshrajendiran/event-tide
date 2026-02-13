package com.eventide.dto;

import com.eventide.model.ActionType;
import lombok.*;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkflowRuleResponse {
    private UUID id;
    private int priority;
    private String condition;
    private ActionType actionType;
    private String actionConfig;
}
