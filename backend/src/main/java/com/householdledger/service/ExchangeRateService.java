package com.householdledger.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdledger.dto.SupportedCurrencyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ExchangeRateService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    // Built-in benchmark rates (USD as base currency) to guarantee 100% offline & test reliability
    private static final Map<String, BigDecimal> FALLBACK_USD_RATES = Map.ofEntries(
            Map.entry("USD", new BigDecimal("1.000000")),
            Map.entry("EUR", new BigDecimal("0.920000")),
            Map.entry("GBP", new BigDecimal("0.790000")),
            Map.entry("CAD", new BigDecimal("1.380000")),
            Map.entry("AUD", new BigDecimal("1.520000")),
            Map.entry("JPY", new BigDecimal("154.000000")),
            Map.entry("CHF", new BigDecimal("0.900000")),
            Map.entry("INR", new BigDecimal("84.000000")),
            Map.entry("CNY", new BigDecimal("7.240000")),
            Map.entry("SGD", new BigDecimal("1.350000")),
            Map.entry("NZD", new BigDecimal("1.680000")),
            Map.entry("AED", new BigDecimal("3.672500")),
            Map.entry("MXN", new BigDecimal("19.800000")),
            Map.entry("BRL", new BigDecimal("5.700000")),
            Map.entry("SEK", new BigDecimal("10.600000")),
            Map.entry("NOK", new BigDecimal("10.900000")),
            Map.entry("THB", new BigDecimal("34.500000")),
            Map.entry("ZAR", new BigDecimal("17.600000"))
    );

    private static final List<SupportedCurrencyResponse> SUPPORTED_CURRENCIES = List.of(
            new SupportedCurrencyResponse("USD", "US Dollar", "$"),
            new SupportedCurrencyResponse("EUR", "Euro", "€"),
            new SupportedCurrencyResponse("GBP", "British Pound", "£"),
            new SupportedCurrencyResponse("CAD", "Canadian Dollar", "CA$"),
            new SupportedCurrencyResponse("AUD", "Australian Dollar", "A$"),
            new SupportedCurrencyResponse("JPY", "Japanese Yen", "¥"),
            new SupportedCurrencyResponse("CHF", "Swiss Franc", "CHF"),
            new SupportedCurrencyResponse("INR", "Indian Rupee", "₹"),
            new SupportedCurrencyResponse("CNY", "Chinese Yuan", "¥"),
            new SupportedCurrencyResponse("SGD", "Singapore Dollar", "S$"),
            new SupportedCurrencyResponse("NZD", "New Zealand Dollar", "NZ$"),
            new SupportedCurrencyResponse("AED", "UAE Dirham", "AED"),
            new SupportedCurrencyResponse("MXN", "Mexican Peso", "Mex$"),
            new SupportedCurrencyResponse("BRL", "Brazilian Real", "R$"),
            new SupportedCurrencyResponse("SEK", "Swedish Krona", "kr"),
            new SupportedCurrencyResponse("NOK", "Norwegian Krone", "kr"),
            new SupportedCurrencyResponse("THB", "Thai Baht", "฿"),
            new SupportedCurrencyResponse("ZAR", "South African Rand", "R")
    );

    // In-memory cache: "FROM_TO" -> CachedRate
    private record CachedRate(BigDecimal rate, long fetchedTimestamp) {}
    private final Map<String, CachedRate> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ExchangeRateService(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(1500))
                .setReadTimeout(Duration.ofMillis(1500))
                .build();
        this.objectMapper = objectMapper;
    }

    public List<SupportedCurrencyResponse> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    /**
     * Resolves the exchange rate to convert an amount from fromCurrency into toCurrency.
     * If user provided a positive customRate, that takes precedence.
     */
    public BigDecimal resolveRate(String fromCurrency, String toCurrency, LocalDate date, BigDecimal customRate) {
        if (customRate != null && customRate.compareTo(BigDecimal.ZERO) > 0) {
            return customRate.setScale(SCALE, ROUNDING);
        }
        return getExchangeRate(fromCurrency, toCurrency, date);
    }

    /**
     * Converts an amount using the resolved exchange rate.
     */
    public BigDecimal convertWithRate(BigDecimal amount, BigDecimal rate) {
        if (amount == null) return BigDecimal.ZERO.setScale(2, ROUNDING);
        BigDecimal effectiveRate = (rate != null) ? rate : BigDecimal.ONE;
        return amount.multiply(effectiveRate).setScale(2, ROUNDING);
    }

    /**
     * Returns how many units of toCurrency equals 1 unit of fromCurrency.
     * E.g. from EUR to USD returns ~1.086957.
     */
    public BigDecimal getExchangeRate(String from, String to, LocalDate date) {
        if (from == null || to == null) {
            return BigDecimal.ONE.setScale(SCALE, ROUNDING);
        }

        String fromUpper = from.trim().toUpperCase();
        String toUpper = to.trim().toUpperCase();

        if (fromUpper.equals(toUpper)) {
            return BigDecimal.ONE.setScale(SCALE, ROUNDING);
        }

        String cacheKey = fromUpper + "_" + toUpper;
        CachedRate cached = cache.get(cacheKey);
        if (cached != null && (System.currentTimeMillis() - cached.fetchedTimestamp() < CACHE_TTL_MS)) {
            return cached.rate();
        }

        BigDecimal rate = fetchOnlineRate(fromUpper, toUpper);
        if (rate == null) {
            rate = calculateFallbackRate(fromUpper, toUpper);
        }

        rate = rate.setScale(SCALE, ROUNDING);
        cache.put(cacheKey, new CachedRate(rate, System.currentTimeMillis()));
        return rate;
    }

    private BigDecimal fetchOnlineRate(String from, String to) {
        try {
            // Using free open.er-api.com endpoint
            String url = "https://open.er-api.com/v6/latest/" + from;
            String json = restTemplate.getForObject(url, String.class);
            if (json != null) {
                JsonNode root = objectMapper.readTree(json);
                if ("success".equalsIgnoreCase(root.path("result").asText())) {
                    JsonNode rates = root.path("rates");
                    if (rates.has(to)) {
                        return new BigDecimal(rates.path(to).asText());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Online exchange rate lookup failed for {} -> {}, falling back to benchmark table: {}",
                    from, to, e.getMessage());
        }
        return null;
    }

    private BigDecimal calculateFallbackRate(String from, String to) {
        BigDecimal fromToUsd = FALLBACK_USD_RATES.getOrDefault(from, BigDecimal.ONE);
        BigDecimal toToUsd = FALLBACK_USD_RATES.getOrDefault(to, BigDecimal.ONE);

        // In FALLBACK_USD_RATES, value is "units per 1 USD".
        // 1 USD = fromToUsd [FROM] => 1 [FROM] = 1 / fromToUsd [USD]
        // 1 USD = toToUsd [TO]
        // Therefore, 1 [FROM] = (toToUsd / fromToUsd) [TO]
        return toToUsd.divide(fromToUsd, 8, ROUNDING).setScale(SCALE, ROUNDING);
    }
}
