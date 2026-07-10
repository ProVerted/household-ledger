package com.householdledger.controller;

import com.householdledger.dto.*;
import com.householdledger.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping("/balances")
    public ResponseEntity<List<BalanceResponse>> getBalances(@PathVariable Long groupId) {
        return ResponseEntity.ok(settlementService.getBalances());
    }

    @GetMapping("/settlements/suggested")
    public ResponseEntity<List<SettlementSuggestionResponse>> getSuggestedSettlements(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(settlementService.getSuggestedSettlements());
    }

    @PostMapping("/settlements/confirm")
    public ResponseEntity<SettlementTransactionResponse> confirmSettlement(
            @PathVariable Long groupId,
            @Valid @RequestBody ConfirmSettlementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settlementService.confirmSettlement(request));
    }

    @GetMapping("/settlements/history")
    public ResponseEntity<List<SettlementTransactionResponse>> getHistory(@PathVariable Long groupId) {
        return ResponseEntity.ok(settlementService.getSettlementHistory());
    }
}
