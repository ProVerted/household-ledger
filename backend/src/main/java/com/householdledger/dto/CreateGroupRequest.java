package com.householdledger.dto;

import com.householdledger.entity.GroupType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.DecimalMin;

import java.time.LocalDate;
import java.math.BigDecimal;

@Getter
@Setter
public class CreateGroupRequest {

    @NotBlank(message = "Group name is required")
    private String name;

    @NotNull(message = "Group type is required")
    private GroupType type;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code, e.g. LKR")
    private String currency;

    private LocalDate  startDate;
    private LocalDate endDate;

    // Optional - defaults to 100.00 on the backend if omitted.
    @DecimalMin(value = "0.00", message = "Approval threshold can't be negative")
    private BigDecimal approvalThreshold;
}
