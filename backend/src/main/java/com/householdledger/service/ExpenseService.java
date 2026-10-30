package com.householdledger.service;

import com.householdledger.dto.*;
import com.householdledger.entity.*;
import com.householdledger.event.NotificationEvent;
import com.householdledger.repository.ExpenseRepository;
import com.householdledger.repository.GroupMembershipRepository;
import com.householdledger.repository.GroupRepository;
import com.householdledger.repository.UserRepository;
import com.householdledger.security.GroupContext;
import com.householdledger.security.SecurityUtils;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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
    private final ExchangeRateService exchangeRateService;
    private final GroupContext groupContext;
    private final ApplicationEventPublisher eventPublisher;

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

        String expenseCurrency = request.getCurrency().trim().toUpperCase();
        String groupBaseCurrency = group.getCurrency().trim().toUpperCase();

        BigDecimal exchangeRate = exchangeRateService.resolveRate(
                expenseCurrency,
                groupBaseCurrency,
                request.getExpenseDate(),
                request.getExchangeRate());

        BigDecimal baseCurrencyAmount = exchangeRateService.convertWithRate(request.getAmount(), exchangeRate);

        // Compare against threshold in the group's base currency
        ExpenseStatus status = baseCurrencyAmount.compareTo(group.getApprovalThreshold()) > 0
                ? ExpenseStatus.PENDING_APPROVAL
                : ExpenseStatus.APPROVED;

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .description(request.getDescription())
                .amount(request.getAmount())
                .currency(expenseCurrency)
                .exchangeRate(exchangeRate)
                .baseCurrencyAmount(baseCurrencyAmount)
                .category(request.getCategory())
                .expenseDate(request.getExpenseDate())
                .splitType(request.getSplitType())
                .status(status)
                .build();

        applySplits(expense, request.getAmount(), baseCurrencyAmount, request.getSplitType(), request.getSplits());

        expense = expenseRepository.save(expense);

        if (expense.getStatus() == ExpenseStatus.PENDING_APPROVAL) {
            notifyApprovers(groupId, expense);
        }

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

        eventPublisher.publishEvent(new NotificationEvent(expense.getPaidBy().getId(),
                "Your expense \"" + expense.getDescription() + "\" was approved."));

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

        eventPublisher.publishEvent(new NotificationEvent(expense.getPaidBy().getId(),
                "Your expense \"" + expense.getDescription() + "\" was rejected."));

        return toResponse(expense);
    }

    private void notifyApprovers(Long groupId, Expense expense) {
        String convertedPreview = !expense.getCurrency().equalsIgnoreCase(expense.getGroup().getCurrency())
                ? " ~ " + expense.getGroup().getCurrency() + " " + expense.getBaseCurrencyAmount()
                : "";

        String message = expense.getPaidBy().getName() + "'s expense \"" + expense.getDescription()
                + "\" (" + expense.getCurrency() + " " + expense.getAmount() + convertedPreview + ") needs your approval.";

        membershipRepository.findByGroupIdAndActiveTrue(groupId).stream()
                .filter(m -> m.getRole() == GroupRole.OWNER || m.getRole() == GroupRole.TREASURER)
                .forEach(m -> eventPublisher.publishEvent(new NotificationEvent(m.getUser().getId(), message)));
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

        String expenseCurrency = request.getCurrency().trim().toUpperCase();
        String groupBaseCurrency = expense.getGroup().getCurrency().trim().toUpperCase();

        BigDecimal exchangeRate = exchangeRateService.resolveRate(
                expenseCurrency,
                groupBaseCurrency,
                request.getExpenseDate(),
                request.getExchangeRate());

        BigDecimal baseCurrencyAmount = exchangeRateService.convertWithRate(request.getAmount(), exchangeRate);

        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setCurrency(expenseCurrency);
        expense.setExchangeRate(exchangeRate);
        expense.setBaseCurrencyAmount(baseCurrencyAmount);
        expense.setCategory(request.getCategory());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setSplitType(request.getSplitType());
        expense.setPaidBy(paidBy);

        expense.clearSplits();
        applySplits(expense, request.getAmount(), baseCurrencyAmount, request.getSplitType(), request.getSplits());

        try {
            expense = expenseRepository.save(expense);
        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This expense was modified by someone else. Please refresh and try again.");
        }

        return toResponse(expense);
    }

    private void applySplits(
            Expense expense,
            BigDecimal amount,
            BigDecimal baseCurrencyAmount,
            SplitType splitType,
            List<SplitInput> inputs) {
        List<SplitCalculationService.ComputedSplit> computedOriginal =
                splitCalculationService.compute(amount, splitType, inputs);

        List<SplitCalculationService.ComputedSplit> computedBase =
                splitCalculationService.compute(baseCurrencyAmount, splitType, inputs);

        Map<Long, User> userCache = new HashMap<>();
        for (int i = 0; i < computedOriginal.size(); i++) {
            SplitCalculationService.ComputedSplit cOriginal = computedOriginal.get(i);
            SplitCalculationService.ComputedSplit cBase = computedBase.get(i);

            User user = userCache.computeIfAbsent(cOriginal.userId(), id ->
                    userRepository.findById(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Split user not found: " + id)));

            ExpenseSplit split = ExpenseSplit.builder()
                    .user(user)
                    .shareAmount(cOriginal.shareAmount())
                    .baseCurrencyShareAmount(cBase.shareAmount())
                    .sharePercentage(cOriginal.sharePercentage())
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

        if (!expense.getGroup().getId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found");
        }
        return expense;
    }

    private ExpenseResponse toResponse(Expense expense) {
        String baseCurrency = expense.getGroup() != null ? expense.getGroup().getCurrency() : expense.getCurrency();

        List<ExpenseSplitResponse> splitResponses = expense.getSplits().stream()
                .map(s -> ExpenseSplitResponse.builder()
                        .userId(s.getUser().getId())
                        .name(s.getUser().getName())
                        .shareAmount(s.getShareAmount())
                        .baseCurrencyShareAmount(s.getBaseCurrencyShareAmount())
                        .sharePercentage(s.getSharePercentage())
                        .build())
                .toList();

        return ExpenseResponse.builder()
                .id(expense.getId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .exchangeRate(expense.getExchangeRate())
                .baseCurrencyAmount(expense.getBaseCurrencyAmount())
                .baseCurrency(baseCurrency)
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
