package com.householdledger.service;

import com.householdledger.dto.BalanceResponse;
import com.householdledger.entity.Expense;
import com.householdledger.entity.ExpenseSplit;
import com.householdledger.entity.GroupMembership;
import com.householdledger.entity.SettlementTransaction;
import com.householdledger.repository.ExpenseRepository;
import com.householdledger.repository.GroupMembershipRepository;
import com.householdledger.repository.SettlementTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final ExpenseRepository expenseRepository;
    private final GroupMembershipRepository membershipRepository;
    private final SettlementTransactionRepository settlementTransactionRepository;

    /**
     * Net balance per member: how much they paid across all expenses, minus how
     * much they owe across all splits, adjusted for any confirmed settlements.
     * All calculations are normalized in the group's base currency using
     * baseCurrencyAmount and baseCurrencyShareAmount to support multi-currency expenses.
     * Positive = the group owes them; negative = they owe the group.
     *
     * Confirmed settlements offset these balances - if A pays B $50 and that
     * gets confirmed, A's debt shrinks by $50 and B's credit shrinks by $50.
     */
    public Map<Long, BigDecimal> computeNetBalances(Long groupId) {
        // Seed every active member at zero first, so someone who hasn't paid or
        // been split with yet still shows up (relevant for the settlement algorithm,
        // which should never need to mention someone with a true zero balance).
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        List<GroupMembership> members = membershipRepository.findByGroupIdAndActiveTrue(groupId);
        for (GroupMembership m : members) {
            balances.put(m.getUser().getId(), BigDecimal.ZERO);
        }

        List<Expense> expenses = expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId);
        for (Expense expense : expenses) {
            // Pending expenses aren't confirmed yet and rejected ones never happened,
            // financially speaking - only approved expenses should move balances.
            if (expense.getStatus() != com.householdledger.entity.ExpenseStatus.APPROVED) {
                continue;
            }

            Long paidByUserId = expense.getPaidBy().getId();
            // Multi-currency normalized: credit paid amount in base currency
            balances.merge(paidByUserId, expense.getBaseCurrencyAmount(), BigDecimal::add);

            for (ExpenseSplit split : expense.getSplits()) {
                Long owingUserId = split.getUser().getId();
                // Multi-currency normalized: debit split share in base currency
                balances.merge(owingUserId, split.getBaseCurrencyShareAmount().negate(), BigDecimal::add);
            }
        }

        List<SettlementTransaction> settlements =
                settlementTransactionRepository.findByGroupIdOrderBySettledAtDesc(groupId);
        for (SettlementTransaction settlement : settlements) {
            // The payer (fromUser) had a debt; paying it moves their balance
            // toward zero, i.e. up. The recipient (toUser) had a credit; being
            // paid moves their balance toward zero too, i.e. down.
            balances.merge(settlement.getFromUser().getId(), settlement.getAmount(), BigDecimal::add);
            balances.merge(settlement.getToUser().getId(), settlement.getAmount().negate(), BigDecimal::add);
        }

        return balances;
    }

    public List<BalanceResponse> getBalances(Long groupId) {
        Map<Long, BigDecimal> balances = computeNetBalances(groupId);
        Map<Long, String> names = new LinkedHashMap<>();
        for (GroupMembership m : membershipRepository.findByGroupIdAndActiveTrue(groupId)) {
            names.put(m.getUser().getId(), m.getUser().getName());
        }

        return balances.entrySet().stream()
                .map(entry -> BalanceResponse.builder()
                        .userId(entry.getKey())
                        .name(names.getOrDefault(entry.getKey(), "Unknown"))
                        .netBalance(entry.getValue().setScale(2, RoundingMode.HALF_UP))
                        .build())
                .toList();
    }
}
