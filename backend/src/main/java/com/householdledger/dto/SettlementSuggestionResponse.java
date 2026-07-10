package com.householdledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class SettlementSuggestionResponse {
    private Long fromUserId;
    private String fromName;
    private Long toUserId;
    private String toName;
    private BigDecimal amount;
}
