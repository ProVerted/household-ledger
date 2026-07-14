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

        List<SplitCalculationService.ComputedSplit> computed =
                splitCalculationService.compute(template.getAmount(), SplitType.EQUAL, inputs);

        ExpenseStatus status = template.getAmount().compareTo(template.getGroup().getApprovalThreshold()) > 0
                ? ExpenseStatus.PENDING_APPROVAL
                : ExpenseStatus.APPROVED;

        Expense expense = Expense.builder()
                .group(template.getGroup())
                .paidBy(template.getPaidBy())
                .description(template.getDescription())
                .amount(template.getAmount())
                .currency(template.getCurrency())
                .category(template.getCategory())
                .expenseDate(today)
                .splitType(SplitType.EQUAL)
                .status(status)
                .build();

        for (SplitCalculationService.ComputedSplit c : computed) {
            User user = activeMembers.stream()
                    .map(GroupMembership::getUser)
                    .filter(u -> u.getId().equals(c.userId()))
                    .findFirst()
                    .orElseThrow();
            expense.addSplit(ExpenseSplit.builder()
                    .user(user)
                    .shareAmount(c.shareAmount())
                    .sharePercentage(c.sharePercentage())
                    .build());
        }

        expenseRepository.save(expense);

        template.setLastGeneratedAt(today);
        templateRepository.save(template);

        notifyGroup(groupId, "Recurring expense \"" + template.getDescription() + "\" ("
                + template.getCurrency() + " " + template.getAmount() + ") was auto-generated for this month.");

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