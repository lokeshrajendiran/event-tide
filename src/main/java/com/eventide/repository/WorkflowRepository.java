package com.eventide.repository;

import com.eventide.model.Workflow;
import com.eventide.model.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Database access for Workflow entities.
 *
 * Spring Data JPA reads the method names and auto-generates SQL queries:
 *   findByEventTypeAndSource("customer.created", "user-service")
 *   → SELECT * FROM workflows WHERE event_type = ? AND source = ?
 *
 * No implementation needed — Spring does it at runtime.
 */
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    // Used by the engine: find the workflow that matches an incoming event
    Optional<Workflow> findByEventTypeAndSourceAndStatus(
            String eventType, String source, WorkflowStatus status);

    // Used by the API: list all workflows, optionally filtered by status
    List<Workflow> findByStatus(WorkflowStatus status);
}
