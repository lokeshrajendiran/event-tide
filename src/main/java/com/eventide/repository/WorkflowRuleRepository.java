package com.eventide.repository;

import com.eventide.model.WorkflowRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Database access for WorkflowRule entities.
 *
 * findByWorkflowIdOrderByPriorityAsc(workflowId)
 * → SELECT * FROM workflow_rules WHERE workflow_id = ? ORDER BY priority ASC
 */
public interface WorkflowRuleRepository extends JpaRepository<WorkflowRule, UUID> {

    List<WorkflowRule> findByWorkflowIdOrderByPriorityAsc(UUID workflowId);
}
