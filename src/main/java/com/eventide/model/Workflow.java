package com.eventide.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Workflow defines WHAT event to listen for and WHERE it comes from.
 * It contains a list of rules that are evaluated when a matching event arrives.
 *
 * Example:
 *   name       = "New Customer Onboarding"
 *   eventType  = "customer.created"
 *   source     = "user-service"
 *   status     = ACTIVE
 *   rules      = [ rule1, rule2, rule3 ]
 */
@Entity
@Table(name = "workflows", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_type", "source"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.ACTIVE;

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC")
    @Builder.Default
    private List<WorkflowRule> rules = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
