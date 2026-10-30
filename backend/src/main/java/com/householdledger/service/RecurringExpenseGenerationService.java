package com.householdledger.service;

import com.householdledger.dto.SplitInput;
import com.householdledger.entity.*;
import com.householdledger.event.NotificationEvent;
import com.householdledger.repository.ExpenseRepository;
import com.householdledger.repository.GroupMembershipRepository;
import com.householdledger.repository.RecurringExpenseTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringExpenseGenerationService {

    private final RecurringExpenseTemplateRepository templateRepository;
    private final ExpenseRepository expenseRepository;
    private final GroupMembershipRepository membershipRepository;
    private final SplitCalculationService splitCalculationService;
    private final ExchangeRateService exchangeRateService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Generates one month's Expense from a template. Re-fetches the template
     * by id (rather than accepting the detached entity the scheduler already
     * had) so that template.getGroup() and .getPaidBy() - both lazy
     * associations - are safe to access here, inside this method's own
     * transaction/session.
     */
    @Transactional
    public void generateOne(Long templateId, LocalDate today) {
        RecurringExpenseTemplate template = templateRepository.findById(templateId).orElse(null);
        if (template == null || !template.isActive()) {
            // Deactivated or deleted between the scheduler's query and now -
            // nothing to do.
            return;
        }

        Long groupId = template.getGroup().getId();
        Long paidByUserId = template.getPaidBy().getId();

        if (!membershipRepository.existsByGroupIdAndUserIdAndActiveTrue(groupId, paidByUserId)) {
            log.warn("Skipping recurring template {}: paidBy user {} is no longer an active member",
                    templateId, paidByUserId);
            notifyGroup(groupId, "Recurring expense \"" + template.getDescription()
                    + "\" was skipped this month - the person who normally pays has left the group. "
                    + "Update it under Recurring Expenses.");
            return;
        }

        List<GroupMembership> activeMembers = membershipRepository.findByGroupIdAndActiveTrue(groupId);

        List<SplitInput> inputs = activeMembers.stream()
                .map(m -> {
                    SplitInput input = new SplitInput();
                    input.setUserId(m.getUser().getId());
                    return input;
                })
                .toList();

        String templateCurrency = template.getCurrency().trim().toUpperCase();
        String groupBaseCurrency = template.getGroup().getCurrency().trim().toUpperCase();

        BigDecimal exchangeRate = exchangeRateService.resolveRate(
                templateCurrency,
                groupBaseCurrency,
                today,
                null);

        BigDecimal baseCurrencyAmount = exchangeRateService.convertWithRate(template.getAmount(), exchangeRate);

        List<SplitCalculationService.ComputedSplit> computedOriginal =
                splitCalculationService.compute(template.getAmount(), SplitType.EQUAL, inputs);

        List<SplitCalculationService.ComputedSplit> computedBase =
                splitCalculationService.compute(baseCurrencyAmount, SplitType.EQUAL, inputs);

        ExpenseStatus status = baseCurrencyAmount.compareTo(template.getGroup().getApprovalThreshold()) > 0
                ? ExpenseStatus.PENDING_APPROVAL
                : ExpenseStatus.APPROVED;

        Expense expense = Expense.builder()
                .group(template.getGroup())
                .paidBy(template.getPaidBy())
                .description(template.getDescription())
                .amount(template.getAmount())
                .currency(templateCurrency)
                .exchangeRate(exchangeRate)
                .baseCurrencyAmount(baseCurrencyAmount)
                .category(template.getCategory())
                .expenseDate(today)
                .splitType(SplitType.EQUAL)
                .status(status)
                .build();

        for (int i = 0; i < computedOriginal.size(); i++) {
            SplitCalculationService.ComputedSplit cOrig = computedOriginal.get(i);
            SplitCalculationService.ComputedSplit cBase = computedBase.get(i);

            User user = activeMembers.stream()
                    .map(GroupMembership::getUser)
                    .filter(u -> u.getId().equals(cOrig.userId()))
                    .findFirst()
                    .orElseThrow();
            expense.addSplit(ExpenseSplit.builder()
                    .user(user)
                    .shareAmount(cOrig.shareAmount())
                    .baseCurrencyShareAmount(cBase.shareAmount())
                    .sharePercentage(cOrig.sharePercentage())
                    .build());
        }

        expenseRepository.save(expense);

        template.setLastGeneratedAt(today);
        templateRepository.save(template);

        String convertedInfo = !templateCurrency.equalsIgnoreCase(groupBaseCurrency)
                ? " ~ " + groupBaseCurrency + " " + baseCurrencyAmount
                : "";

        notifyGroup(groupId, "Recurring expense \"" + template.getDescription() + "\" ("
                + template.getCurrency() + " " + template.getAmount() + convertedInfo + ") was auto-generated for this month.");

        if (status == ExpenseStatus.PENDING_APPROVAL) {
            eventPublisher.publishEvent(new NotificationEvent(paidByUserId,
                    "Your recurring expense \"" + template.getDescription()
                            + "\" is above the group's approval threshold and needs sign-off."));
        }
    }

    private void notifyGroup(Long groupId, String message) {
        for (GroupMembership m : membershipRepository.findByGroupIdAndActiveTrue(groupId)) {
            eventPublisher.publishEvent(new NotificationEvent(m.getUser().getId(), message));
        }
    }
}
