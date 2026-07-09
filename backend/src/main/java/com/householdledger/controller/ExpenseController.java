package com.householdledger.controller;

import com.householdledger.dto.CreateExpenseRequest;
import com.householdledger.dto.ExpenseResponse;
import com.householdledger.dto.UpdateExpenseRequest;
import com.householdledger.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    // groupId is consumed by GroupContextFilter before this runs; ExpenseService
    // reads the verified id from GroupContext rather than trusting this path
    // variable a second time. It's still declared here so Spring routes correctly
    // and so the URL is self-documenting.
    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.createExpense(request));
    }

    @GetMapping
    public ResponseEntity<List<ExpenseResponse>> getExpenses(@PathVariable Long groupId) {
        return ResponseEntity.ok(expenseService.getExpenses());
    }

    @GetMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> getExpense(
            @PathVariable Long groupId,
            @PathVariable Long expenseId) {
        return ResponseEntity.ok(expenseService.getExpense(expenseId));
    }

    @PatchMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> updateExpense(
            @PathVariable Long groupId,
            @PathVariable Long expenseId,
            @Valid @RequestBody UpdateExpenseRequest request) {
        return ResponseEntity.ok(expenseService.updateExpense(expenseId, request));
    }
}
