package com.householdledger.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SplitInput {

    @NotNull(message = "userId is required for each split")
    private Long userId;

    // EQUAL: ignored (every listed user gets an equal share)
    // PERCENTAGE: this is the percentage (0-100), must sum to 100 across all splits
    // EXACT: this is the exact amount owed, must sum to the expense's total amount
    private BigDecimal value;
}
