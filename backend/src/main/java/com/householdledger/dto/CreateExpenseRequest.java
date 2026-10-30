package com.householdledger.dto;

import com.householdledger.entity.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CreateExpenseRequest {

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency;

    // Optional user-specified exchange rate to the group's base currency
    // (e.g. from credit card bill). If omitted, the system resolves it automatically.
    @DecimalMin(value = "0.000001", message = "Exchange rate must be positive")
    private BigDecimal exchangeRate;

    private String category;

    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

    // Defaults to the current user if omitted.
    private Long paidByUserId;

    @NotNull(message = "Split type is required")
    private SplitType splitType;

    @NotEmpty(message = "At least one split is required")
    @Valid
    private List<SplitInput> splits;
}
