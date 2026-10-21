package com.householdledger.service;

import com.householdledger.dto.SettlementSuggestionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SettlementCalculationServiceTest {

    private SettlementCalculationService settlementCalculationService;

    @BeforeEach
    void setUp() {
        settlementCalculationService = new SettlementCalculationService();
    }

    @Test
    @DisplayName("Single debtor and single creditor: exactly one direct settlement is suggested")
    void testTwoPartySettlement() {
        Map<Long, BigDecimal> netBalances = new HashMap<>();
        netBalances.put(1L, new BigDecimal("50.00"));  // Creditor: owed $50
        netBalances.put(2L, new BigDecimal("-50.00")); // Debtor: owes $50

        Map<Long, String> names = Map.of(1L, "Alice", 2L, "Bob");

        List<SettlementSuggestionResponse> result =
                settlementCalculationService.suggestSettlements(netBalances, names);

        assertEquals(1, result.size());
        SettlementSuggestionResponse suggestion = result.get(0);
        assertEquals(2L, suggestion.getFromUserId());
        assertEquals("Bob", suggestion.getFromName());
        assertEquals(1L, suggestion.getToUserId());
        assertEquals("Alice", suggestion.getToName());
        assertEquals(new BigDecimal("50.00"), suggestion.getAmount());
    }

    @Test
    @DisplayName("Three parties with circular debt: transactions minimized to 2 payments")
    void testThreePartyMinimization() {
        // Alice is owed $30, Bob is owed $10, Charlie owes $40
        Map<Long, BigDecimal> netBalances = new HashMap<>();
        netBalances.put(1L, new BigDecimal("30.00"));
        netBalances.put(2L, new BigDecimal("10.00"));
        netBalances.put(3L, new BigDecimal("-40.00"));

        Map<Long, String> names = Map.of(1L, "Alice", 2L, "Bob", 3L, "Charlie");

        List<SettlementSuggestionResponse> result =
                settlementCalculationService.suggestSettlements(netBalances, names);

        // Charlie should settle with Alice ($30) and Bob ($10) -> exactly 2 transactions
        assertEquals(2, result.size());

        BigDecimal totalSettled = result.stream()
                .map(SettlementSuggestionResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("40.00"), totalSettled);

        // First suggestion matches largest creditor (Alice) with largest debtor (Charlie)
        assertEquals(3L, result.get(0).getFromUserId());
        assertEquals(1L, result.get(0).getToUserId());
        assertEquals(new BigDecimal("30.00"), result.get(0).getAmount());

        // Second suggestion matches remaining debtor (Charlie) with next creditor (Bob)
        assertEquals(3L, result.get(1).getFromUserId());
        assertEquals(2L, result.get(1).getToUserId());
        assertEquals(new BigDecimal("10.00"), result.get(1).getAmount());
    }

    @Test
    @DisplayName("Everyone is settled: empty suggestion list returned")
    void testAlreadySettled() {
        Map<Long, BigDecimal> netBalances = new HashMap<>();
        netBalances.put(1L, BigDecimal.ZERO);
        netBalances.put(2L, BigDecimal.ZERO);

        Map<Long, String> names = Map.of(1L, "Alice", 2L, "Bob");

        List<SettlementSuggestionResponse> result =
                settlementCalculationService.suggestSettlements(netBalances, names);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Micro balances below epsilon (0.005) are ignored as settled rounding drift")
    void testEpsilonTolerance() {
        Map<Long, BigDecimal> netBalances = new HashMap<>();
        netBalances.put(1L, new BigDecimal("0.004"));
        netBalances.put(2L, new BigDecimal("-0.004"));

        Map<Long, String> names = Map.of(1L, "Alice", 2L, "Bob");

        List<SettlementSuggestionResponse> result =
                settlementCalculationService.suggestSettlements(netBalances, names);

        assertTrue(result.isEmpty());
    }
}
