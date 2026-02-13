package com.eventide.controller;

import com.eventide.dto.WorkflowRequest;
import com.eventide.dto.WorkflowResponse;
import com.eventide.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping
    public ResponseEntity<WorkflowResponse> create(@Valid @RequestBody WorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<WorkflowResponse>> listAll() {
        return ResponseEntity.ok(workflowService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkflowResponse> update(
            @PathVariable UUID id, @Valid @RequestBody WorkflowRequest request) {
        return ResponseEntity.ok(workflowService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        workflowService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<WorkflowResponse> toggleStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.toggleStatus(id));
    }
}
