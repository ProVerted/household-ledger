package com.householdledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdledger.dto.SupportedCurrencyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeRateServiceTest {

    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() {
        exchangeRateService = new ExchangeRateService(new RestTemplateBuilder(), new ObjectMapper());
    }

    @Test
    @DisplayName("Identity conversion: same currency yields exact 1.000000 rate")
    void testSameCurrencyReturnsOne() {
        BigDecimal rate = exchangeRateService.getExchangeRate("USD", "USD", LocalDate.now());
        assertEquals(new BigDecimal("1.000000"), rate);

        BigDecimal eurRate = exchangeRateService.getExchangeRate("eur", "EUR", LocalDate.now());
        assertEquals(new BigDecimal("1.000000"), eurRate);
    }

    @Test
    @DisplayName("Fallback conversion: EUR to USD resolves to benchmark exchange rate (~1.086957)")
    void testEurToUsdFallback() {
        BigDecimal rate = exchangeRateService.getExchangeRate("EUR", "USD", LocalDate.now());
        assertNotNull(rate);
        assertTrue(rate.compareTo(new BigDecimal("1.05")) > 0);
        assertTrue(rate.compareTo(new BigDecimal("1.12")) < 0);
    }

    @Test
    @DisplayName("Custom exchange rate override takes priority over automatic lookup")
    void testCustomRateTakesPriority() {
        BigDecimal custom = new BigDecimal("1.250000");
        BigDecimal rate = exchangeRateService.resolveRate("EUR", "USD", LocalDate.now(), custom);
        assertEquals(new BigDecimal("1.250000"), rate);
    }

    @Test
    @DisplayName("Convert with rate: converts 100 EUR at rate 1.100000 to 110.00 USD")
    void testConvertWithRate() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal rate = new BigDecimal("1.100000");
        BigDecimal converted = exchangeRateService.convertWithRate(amount, rate);
        assertEquals(new BigDecimal("110.00"), converted);
    }

    @Test
    @DisplayName("Supported currencies list includes major global currencies")
    void testSupportedCurrencies() {
        List<SupportedCurrencyResponse> list = exchangeRateService.getSupportedCurrencies();
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(c -> "USD".equals(c.getCode())));
        assertTrue(list.stream().anyMatch(c -> "EUR".equals(c.getCode())));
        assertTrue(list.stream().anyMatch(c -> "GBP".equals(c.getCode())));
    }
}
