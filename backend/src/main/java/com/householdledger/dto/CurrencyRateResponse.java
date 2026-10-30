package com.householdledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyRateResponse {
    private String from;
    private String to;
    private BigDecimal rate;
    private LocalDate date;
}
