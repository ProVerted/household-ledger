package com.householdledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class ExpenseSplitResponse {
    private Long userId;
    private String name;
    private BigDecimal shareAmount;
    private BigDecimal sharePercentage;
}
