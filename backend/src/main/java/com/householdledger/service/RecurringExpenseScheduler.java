package com.householdledger.service;

import com.householdledger.entity.RecurringExpenseTemplate;
import com.householdledger.repository.RecurringExpenseTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Runs once a day and decides *which* active templates are due, then hands
 * each one off to RecurringExpenseGenerationService to actually create the
 * Expense.
 *
 * This is deliberately a separate bean from the generation logic, for a
 * subtle but important reason: if generateOne() were just another
 * @Transactional method on this same class, calling it from within
 * generateDueExpenses() (this@this method call) would be a same-class
 * self-invocation - Spring's @Transactional works via a proxy wrapping the
 * bean, and calling a method on "this" bypasses that proxy entirely, silently
 * running with NO transaction at all. Splitting the scheduled trigger and the
 * transactional work into two different beans means the call between them
 * goes through the real Spring-managed proxy, so @Transactional on
 * generateOne() actually takes effect.
 *
 * The other reason this method itself stays untransactional: it queries
 * templates for scalar fields only (dayOfMonth, lastGeneratedAt, id) to decide
 * what's due, and deliberately does NOT touch template.getGroup() or
 * .getPaidBy() here - those are lazy associations, and this method has no
 * open Hibernate session (scheduled jobs don't get Spring's open-session-in-view
 * behavior the way HTTP requests do). Each due template's full data is instead
 * safely re-loaded inside its own transaction over in generateOne().
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringExpenseScheduler {

    private final RecurringExpenseTemplateRepository templateRepository;
    private final RecurringExpenseGenerationService generationService;

    @Scheduled(cron = "0 0 1 * * *") // once daily at 01:00
    public void generateDueExpenses() {
        LocalDate today = LocalDate.now();

        List<Long> dueTemplateIds = templateRepository.findByActiveTrue().stream()
                .filter(t -> isDue(t, today))
                .map(RecurringExpenseTemplate::getId)
                .toList();

        // Each template gets its own try/catch: one template failing (its
        // paidBy user left, a transient DB hiccup, whatever) must not stop
        // every other template - in this group or any other - from
        // generating that day.
        for (Long templateId : dueTemplateIds) {
            try {
                generationService.generateOne(templateId, today);
            } catch (Exception e) {
                log.error("Failed to generate recurring expense for template {}: {}", templateId, e.getMessage());
            }
        }
    }

    private boolean isDue(RecurringExpenseTemplate template, LocalDate today) {
        if (template.getLastGeneratedAt() != null
                && YearMonth.from(template.getLastGeneratedAt()).equals(YearMonth.from(today))) {
            return false;
        }

        boolean isLastDayOfMonth = today.equals(YearMonth.from(today).atEndOfMonth());
        boolean clampedToMonthEnd = isLastDayOfMonth && template.getDayOfMonth() > today.lengthOfMonth();

        return today.getDayOfMonth() == template.getDayOfMonth() || clampedToMonthEnd;
    }
}