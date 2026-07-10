package com.householdledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class BalanceResponse {
    private Long userId;
    private String name;
    // Positive = the group owes this person money (they're a net creditor).
    // Negative = this person owes the group money (they're a net debtor).
    private BigDecimal netBalance;
}
