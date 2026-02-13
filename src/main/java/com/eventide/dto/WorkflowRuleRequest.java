package com.eventide.dto;

import com.eventide.model.ActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkflowRuleRequest {

    private int priority;

    // Condition to evaluate against event payload (null = always matches)
    private String condition;

    @NotNull(message = "actionType is required")
    private ActionType actionType;

    @NotBlank(message = "actionConfig is required")
    private String actionConfig;
}
