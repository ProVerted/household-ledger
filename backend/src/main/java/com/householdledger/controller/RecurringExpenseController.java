package com.householdledger.controller;

import com.householdledger.dto.CreateRecurringExpenseRequest;
import com.householdledger.dto.RecurringExpenseResponse;
import com.householdledger.service.RecurringExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/recurring")
@RequiredArgsConstructor
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    @PostMapping
    @PreAuthorize("hasPermission(#groupId, 'group', 'MANAGE_RECURRING')")
    public ResponseEntity<RecurringExpenseResponse> createTemplate(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateRecurringExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringExpenseService.createTemplate(request));
    }

    // Any active member can view the templates, not just OWNER/TREASURER -
    // this is informational, same visibility level as viewing expenses.
    @GetMapping
    public ResponseEntity<List<RecurringExpenseResponse>> getTemplates(@PathVariable Long groupId) {
        return ResponseEntity.ok(recurringExpenseService.getTemplates());
    }

    @PatchMapping("/{templateId}/deactivate")
    @PreAuthorize("hasPermission(#groupId, 'group', 'MANAGE_RECURRING')")
    public ResponseEntity<Void> deactivate(@PathVariable Long groupId, @PathVariable Long templateId) {
        recurringExpenseService.deactivate(templateId);
        return ResponseEntity.noContent().build();
    }
}