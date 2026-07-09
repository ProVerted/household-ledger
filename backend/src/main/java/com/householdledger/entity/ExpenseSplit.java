package com.householdledger.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "expense_split")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseSplit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Always the final computed amount this person owes for this expense,
    // regardless of whether splitType was EQUAL, PERCENTAGE, or EXACT -
    // this is what balance/settlement calculations in Phase 4 will sum directly,
    // without needing to know how it was originally split.
    @Column(name = "share_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal shareAmount;

    // Only populated when the parent expense's splitType == PERCENTAGE.
    // Kept for display/edit purposes ("this person owes 30%"), not used in
    // balance math directly.
    @Column(name = "share_percentage", precision = 5, scale = 2)
    private BigDecimal sharePercentage;
}
