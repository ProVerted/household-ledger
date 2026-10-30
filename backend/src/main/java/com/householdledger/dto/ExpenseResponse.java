package com.householdledger.dto;

import com.householdledger.entity.ExpenseStatus;
import com.householdledger.entity.SplitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ExpenseResponse {
    private Long id;
    private String description;
    private BigDecimal amount;
    private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal baseCurrencyAmount;
    private String baseCurrency;
    private String category;
    private LocalDate expenseDate;
    private Long paidByUserId;
    private String paidByName;
    private SplitType splitType;
    private ExpenseStatus status;
    private Long version;
    private List<ExpenseSplitResponse> splits;
}
