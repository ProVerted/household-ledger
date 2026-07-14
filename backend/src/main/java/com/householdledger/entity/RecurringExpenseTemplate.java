package com.householdledger.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A recurring bill (rent, utilities) that RecurringExpenseScheduler turns into
 * a real Expense every month.
 *
 * Deliberately scoped to EQUAL splits only, computed fresh against whichever
 * members are active at generation time (see RecurringExpenseGenerationService)
 * rather than storing a frozen per-user split the way a one-off Expense does.
 * A frozen split has no well-defined meaning once someone leaves the group
 * mid-lease - equal-split-among-current-members is both simpler and the
 * actually-correct behavior for a shared household bill. PERCENTAGE/EXACT
 * recurring splits are a reasonable v2 extension, not needed here.
 */
@Entity
@Table(name = "recurring_expense_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringExpenseTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    private String category;

    // Who fronts the money each month (e.g. the treasurer paying the landlord).
    // Re-validated as an active member every time the template fires - if
    // they've since left, that month's generation is skipped and the group is
    // notified, rather than the whole scheduled job failing.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by_user_id", nullable = false)
    private User paidBy;

    // 1-31. Clamped to month-end by the generator for months that don't have
    // this many days (e.g. dayOfMonth=31 fires on Feb 28/29 instead of never).
    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    // Soft flag, not delete - same reasoning as GroupMembership.active: past
    // expenses this template already generated shouldn't be affected, and
    // someone might reactivate it later (e.g. moving back into a lease).
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    // Guards against double-generation if the scheduled job somehow runs twice
    // in the same month (manual trigger for testing, server restart, etc).
    @Column(name = "last_generated_at")
    private LocalDate lastGeneratedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}