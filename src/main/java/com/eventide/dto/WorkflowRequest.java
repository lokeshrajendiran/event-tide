package com.eventide.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkflowRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    @NotBlank(message = "eventType is required")
    private String eventType;

    @NotBlank(message = "source is required")
    private String source;

    @Valid
    private List<WorkflowRuleRequest> rules;
}
