package com.householdledger.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expense")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense extends AbstractAuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by_user_id", nullable = false)
    private User paidBy;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    // Exchange rate to convert expense currency into the group's base currency
    // (e.g. 1.080000 for EUR to USD). Defaults to 1.000000 when same currency.
    @Column(name = "exchange_rate", nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    // The normalized expense amount in group's base currency.
    // Balances and debt settlements operate on this amount.
    @Column(name = "base_currency_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseCurrencyAmount;

    private String category;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false)
    private SplitType splitType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExpenseStatus status = ExpenseStatus.APPROVED;

    // Optimistic locking: every update must send back the version it read.
    // A stale write (someone else edited it first) throws OptimisticLockException,
    // which the service layer turns into a 409 Conflict instead of silently
    // clobbering the other person's change.
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExpenseSplit> splits = new ArrayList<>();

    public BigDecimal getExchangeRate() {
        return exchangeRate != null ? exchangeRate : BigDecimal.ONE;
    }

    public BigDecimal getBaseCurrencyAmount() {
        return baseCurrencyAmount != null ? baseCurrencyAmount : amount;
    }

    public void addSplit(ExpenseSplit split) {
        splits.add(split);
        split.setExpense(this);
    }

    public void clearSplits() {
        splits.forEach(s -> s.setExpense(null));
        splits.clear();
    }
}
