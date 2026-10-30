package com.householdledger.controller;

import com.householdledger.dto.CurrencyRateResponse;
import com.householdledger.dto.SupportedCurrencyResponse;
import com.householdledger.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping
    public ResponseEntity<List<SupportedCurrencyResponse>> getSupportedCurrencies() {
        return ResponseEntity.ok(exchangeRateService.getSupportedCurrencies());
    }

    @GetMapping("/rate")
    public ResponseEntity<CurrencyRateResponse> getRate(
            @RequestParam String from,
            @RequestParam String to) {
        BigDecimal rate = exchangeRateService.getExchangeRate(from, to, LocalDate.now());
        return ResponseEntity.ok(CurrencyRateResponse.builder()
                .from(from.toUpperCase())
                .to(to.toUpperCase())
                .rate(rate)
                .date(LocalDate.now())
                .build());
    }
}
