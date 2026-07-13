package com.householdledger.dto;

import com.householdledger.entity.GroupRole;
import com.householdledger.entity.GroupType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class GroupResponse {
    private Long id;
    private String name;
    private GroupType type;
    private String currency;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal approvalThreshold;
    // The requesting user's role within this specific group -
    // this is what lets the Angular UI show/hide actions per group.
    private GroupRole myRole;
}
