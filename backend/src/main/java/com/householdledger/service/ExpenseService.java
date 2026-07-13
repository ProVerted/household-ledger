package com.householdledger.service;

import com.householdledger.dto.*;
import com.householdledger.entity.*;
import com.householdledger.repository.ExpenseRepository;
import com.householdledger.repository.GroupMembershipRepository;
import com.householdledger.repository.GroupRepository;
import com.householdledger.repository.UserRepository;
import com.householdledger.security.GroupContext;
import com.householdledger.security.SecurityUtils;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final SplitCalculationService splitCalculationService;
    private final GroupContext groupContext;

    @Transactional
    public ExpenseResponse createExpense(CreateExpenseRequest request) {
        Long groupId = groupContext.getGroupId();
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long paidByUserId = request.getPaidByUserId() != null ? request.getPaidByUserId() : currentUserId;

        validateAllUsersAreActiveMembers(groupId, request.getSplits());

        User paidBy = userRepository.findById(paidByUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "paidByUserId not found"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        // Expenses above the group's threshold need sign-off before they count
        // toward balances; everything at or below it is auto-approved so small,
        // routine expenses don't create needless approval friction.
        ExpenseStatus status = request.getAmount().compareTo(group.getApprovalThreshold()) > 0
                ? ExpenseStatus.PENDING_APPROVAL
                : ExpenseStatus.APPROVED;

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .description(request.getDescription())
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .category(request.getCategory())
                .expenseDate(request.getExpenseDate())
                .splitType(request.getSplitType())
                .status(status)
                .build();

        applySplits(expense, request.getAmount(), request.getSplitType(), request.getSplits());

        expense = expenseRepository.save(expense);
        return toResponse(expense);
    }

    @Transactional
    public ExpenseResponse approveExpense(Long expenseId) {
        Expense expense = findInActiveGroup(expenseId);
        if (expense.getStatus() != ExpenseStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This expense isn't awaiting approval");
        }
        expense.setStatus(ExpenseStatus.APPROVED);
        expense = expenseRepository.save(expense);
        return toResponse(expense);
    }

    @Transactional
    public ExpenseResponse rejectExpense(Long expenseId) {
        Expense expense = findInActiveGroup(expenseId);
        if (expense.getStatus() != ExpenseStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This expense isn't awaiting approval");
        }
        expense.setStatus(ExpenseStatus.REJECTED);
        expense = expenseRepository.save(expense);
        return toResponse(expense);
    }

    public List<ExpenseResponse> getExpenses() {
        Long groupId = groupContext.getGroupId();
        return expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ExpenseResponse getExpense(Long expenseId) {
        Expense expense = findInActiveGroup(expenseId);
        return toResponse(expense);
    }

    @Transactional
    public ExpenseResponse updateExpense(Long expenseId, UpdateExpenseRequest request) {
        Expense expense = findInActiveGroup(expenseId);

        // Optimistic lock check: the client must present the version it last read.
        // If someone else edited (and saved) this expense in the meantime, the
        // version on the row has already moved on. JPA's own @Version check at
        // flush time would also catch a mismatch, but checking explicitly here
        // lets us return a clean 409 with a clear message rather than a raw
        // persistence exception leaking to the client.
        if (!expense.getVersion().equals(request.getVersion())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This expense was modified by someone else. Please refresh and try again.");
        }

        Long paidByUserId = request.getPaidByUserId() != null
                ? request.getPaidByUserId()
                : expense.getPaidBy().getId();

        validateAllUsersAreActiveMembers(groupContext.getGroupId(), request.getSplits());

        User paidBy = userRepository.findById(paidByUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "paidByUserId not found"));

        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setCurrency(request.getCurrency().toUpperCase());
        expense.setCategory(request.getCategory());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setSplitType(request.getSplitType());
        expense.setPaidBy(paidBy);

        expense.clearSplits();
        applySplits(expense, request.getAmount(), request.getSplitType(), request.getSplits());

        try {
            expense = expenseRepository.save(expense);
        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This expense was modified by someone else. Please refresh and try again.");
        }

        return toResponse(expense);
    }

    private void applySplits(Expense expense, java.math.BigDecimal amount, SplitType splitType, List<SplitInput> inputs) {
        List<SplitCalculationService.ComputedSplit> computed =
                splitCalculationService.compute(amount, splitType, inputs);

        Map<Long, User> userCache = new HashMap<>();
        for (SplitCalculationService.ComputedSplit c : computed) {
            User user = userCache.computeIfAbsent(c.userId(), id ->
                    userRepository.findById(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Split user not found: " + id)));

            ExpenseSplit split = ExpenseSplit.builder()
                    .user(user)
                    .shareAmount(c.shareAmount())
                    .sharePercentage(c.sharePercentage())
                    .build();
            expense.addSplit(split);
        }
    }

    private void validateAllUsersAreActiveMembers(Long groupId, List<SplitInput> splits) {
        for (SplitInput split : splits) {
            if (!membershipRepository.existsByGroupIdAndUserIdAndActiveTrue(groupId, split.getUserId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "User " + split.getUserId() + " is not an active member of this group");
            }
        }
    }

    private Expense findInActiveGroup(Long expenseId) {
        Long groupId = groupContext.getGroupId();
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));

        // Defense in depth: GroupContextFilter already verified the caller is a
        // member of the group in the URL, but we still confirm this specific
        // expense actually belongs to that group before returning/mutating it.
        if (!expense.getGroup().getId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found");
        }
        return expense;
    }

    private ExpenseResponse toResponse(Expense expense) {
        List<ExpenseSplitResponse> splitResponses = expense.getSplits().stream()
                .map(s -> ExpenseSplitResponse.builder()
                        .userId(s.getUser().getId())
                        .name(s.getUser().getName())
                        .shareAmount(s.getShareAmount())
                        .sharePercentage(s.getSharePercentage())
                        .build())
                .toList();

        return ExpenseResponse.builder()
                .id(expense.getId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .category(expense.getCategory())
                .expenseDate(expense.getExpenseDate())
                .paidByUserId(expense.getPaidBy().getId())
                .paidByName(expense.getPaidBy().getName())
                .splitType(expense.getSplitType())
                .status(expense.getStatus())
                .version(expense.getVersion())
                .splits(splitResponses)
                .build();
    }
}
