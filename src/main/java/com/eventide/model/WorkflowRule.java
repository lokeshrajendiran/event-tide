package com.eventide.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * A WorkflowRule defines a single condition → action pair.
 *
 * When the parent Workflow's event arrives:
 *   1. Rules are evaluated in priority order (lowest first)
 *   2. If the condition matches the event payload → the action fires
 *
 * Example:
 *   priority     = 1
 *   condition    = "payload.plan == 'enterprise'"
 *   actionType   = KAFKA
 *   actionConfig = {"topic": "onboarding-enterprise", "key": "customer-id"}
 */
@Entity
@Table(name = "workflow_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkflowRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    @JsonIgnore
    private Workflow workflow;

    @Column(nullable = false)
    private int priority;

    /**
     * Condition expression evaluated against the event payload.
     * Uses simple expression syntax: "payload.field == 'value'"
     * Null or empty = always matches (catch-all rule).
     */
    @Column(columnDefinition = "TEXT")
    private String condition;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    /**
     * JSON string containing action-specific configuration.
     * For KAFKA:   {"topic": "target-topic", "key": "optional-key"}
     * For WEBHOOK: {"url": "https://...", "headers": {"Auth": "..."}}
     * For HTTP:    {"url": "https://...", "method": "POST", "body": "..."}
     */
    @Column(name = "action_config", columnDefinition = "TEXT", nullable = false)
    private String actionConfig;
}
