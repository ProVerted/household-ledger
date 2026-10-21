package com.householdledger.service;

import com.householdledger.dto.BalanceResponse;
import com.householdledger.entity.*;
import com.householdledger.repository.ExpenseRepository;
import com.householdledger.repository.GroupMembershipRepository;
import com.householdledger.repository.SettlementTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupMembershipRepository membershipRepository;

    @Mock
    private SettlementTransactionRepository settlementTransactionRepository;

    @InjectMocks
    private BalanceService balanceService;

    @Test
    @DisplayName("Compute net balances: approved expenses credit payer and debit splitters, pending/rejected ignored")
    void testComputeNetBalancesWithApprovalStatuses() {
        Long groupId = 10L;

        User alice = User.builder().id(1L).name("Alice").email("alice@test.com").build();
        User bob = User.builder().id(2L).name("Bob").email("bob@test.com").build();

        Group group = Group.builder().id(groupId).name("Test Group").build();

        GroupMembership memAlice = GroupMembership.builder().id(101L).group(group).user(alice).active(true).build();
        GroupMembership memBob = GroupMembership.builder().id(102L).group(group).user(bob).active(true).build();

        when(membershipRepository.findByGroupIdAndActiveTrue(groupId)).thenReturn(List.of(memAlice, memBob));

        // Approved expense: Alice paid $40, split equally ($20 each)
        Expense approvedExpense = Expense.builder()
                .id(1L)
                .group(group)
                .paidBy(alice)
                .amount(new BigDecimal("40.00"))
                .currency("USD")
                .expenseDate(LocalDate.now())
                .status(ExpenseStatus.APPROVED)
                .build();

        ExpenseSplit splitAlice = ExpenseSplit.builder()
                .id(11L)
                .expense(approvedExpense)
                .user(alice)
                .shareAmount(new BigDecimal("20.00"))
                .build();

        ExpenseSplit splitBob = ExpenseSplit.builder()
                .id(12L)
                .expense(approvedExpense)
                .user(bob)
                .shareAmount(new BigDecimal("20.00"))
                .build();

        approvedExpense.setSplits(List.of(splitAlice, splitBob));

        // Pending expense: Bob paid $100, pending approval (must NOT affect balances)
        Expense pendingExpense = Expense.builder()
                .id(2L)
                .group(group)
                .paidBy(bob)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .expenseDate(LocalDate.now())
                .status(ExpenseStatus.PENDING_APPROVAL)
                .build();

        when(expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId))
                .thenReturn(List.of(approvedExpense, pendingExpense));
        when(settlementTransactionRepository.findByGroupIdOrderBySettledAtDesc(groupId))
                .thenReturn(List.of());

        Map<Long, BigDecimal> netBalances = balanceService.computeNetBalances(groupId);

        // Alice: paid +40, owed share -20 => net +20
        assertEquals(new BigDecimal("20.00"), netBalances.get(1L));
        // Bob: paid 0, owed share -20 => net -20
        assertEquals(new BigDecimal("-20.00"), netBalances.get(2L));
    }

    @Test
    @DisplayName("Confirmed settlements offset balances toward zero")
    void testConfirmedSettlementsOffsetBalances() {
        Long groupId = 10L;

        User alice = User.builder().id(1L).name("Alice").email("alice@test.com").build();
        User bob = User.builder().id(2L).name("Bob").email("bob@test.com").build();
        Group group = Group.builder().id(groupId).name("Test Group").build();

        GroupMembership memAlice = GroupMembership.builder().id(101L).group(group).user(alice).active(true).build();
        GroupMembership memBob = GroupMembership.builder().id(102L).group(group).user(bob).active(true).build();

        when(membershipRepository.findByGroupIdAndActiveTrue(groupId)).thenReturn(List.of(memAlice, memBob));

        // Expense: Alice paid $50 for Bob entirely (Alice +50, -0 owed; Bob +0 paid, -50 owed)
        Expense expense = Expense.builder()
                .id(1L)
                .group(group)
                .paidBy(alice)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .expenseDate(LocalDate.now())
                .status(ExpenseStatus.APPROVED)
                .build();

        ExpenseSplit splitBob = ExpenseSplit.builder()
                .id(11L)
                .expense(expense)
                .user(bob)
                .shareAmount(new BigDecimal("50.00"))
                .build();

        expense.setSplits(List.of(splitBob));

        // Bob paid Alice $50 in confirmed settlement
        SettlementTransaction settlement = SettlementTransaction.builder()
                .id(501L)
                .group(group)
                .fromUser(bob)
                .toUser(alice)
                .amount(new BigDecimal("50.00"))
                .settledAt(Instant.now())
                .build();

        when(expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId)).thenReturn(List.of(expense));
        when(settlementTransactionRepository.findByGroupIdOrderBySettledAtDesc(groupId)).thenReturn(List.of(settlement));

        Map<Long, BigDecimal> netBalances = balanceService.computeNetBalances(groupId);

        // Both should now be 0
        assertEquals(new BigDecimal("0.00"), netBalances.get(1L));
        assertEquals(new BigDecimal("0.00"), netBalances.get(2L));
    }
}
