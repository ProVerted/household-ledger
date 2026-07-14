package com.householdledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class RecurringExpenseResponse {
    private Long id;
    private String description;
    private BigDecimal amount;
    private String currency;
    private String category;
    private int dayOfMonth;
    private boolean active;
    private Long paidByUserId;
    private String paidByName;
    private LocalDate lastGeneratedAt;
}