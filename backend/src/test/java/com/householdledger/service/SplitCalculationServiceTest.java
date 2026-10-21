package com.householdledger.service;

import com.householdledger.dto.SplitInput;
import com.householdledger.entity.SplitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SplitCalculationServiceTest {

    private SplitCalculationService splitCalculationService;

    @BeforeEach
    void setUp() {
        splitCalculationService = new SplitCalculationService();
    }

    @Test
    @DisplayName("EQUAL split: cleanly divisible amounts split identically")
    void testEqualSplitCleanDivision() {
        BigDecimal total = new BigDecimal("60.00");
        List<SplitInput> inputs = List.of(
                new SplitInput(1L, null),
                new SplitInput(2L, null),
                new SplitInput(3L, null)
        );

        List<SplitCalculationService.ComputedSplit> splits =
                splitCalculationService.compute(total, SplitType.EQUAL, inputs);

        assertEquals(3, splits.size());
        assertEquals(new BigDecimal("20.00"), splits.get(0).shareAmount());
        assertEquals(new BigDecimal("20.00"), splits.get(1).shareAmount());
        assertEquals(new BigDecimal("20.00"), splits.get(2).shareAmount());

        BigDecimal sum = splits.stream().map(SplitCalculationService.ComputedSplit::shareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(total, sum);
    }

    @Test
    @DisplayName("EQUAL split with penny remainder: leftover cents allocated one-by-one to early members")
    void testEqualSplitPennyRemainder() {
        // $10.00 divided by 3 = $3.33 each with $0.01 remainder -> first member gets $3.34
        BigDecimal total = new BigDecimal("10.00");
        List<SplitInput> inputs = List.of(
                new SplitInput(1L, null),
                new SplitInput(2L, null),
                new SplitInput(3L, null)
        );

        List<SplitCalculationService.ComputedSplit> splits =
                splitCalculationService.compute(total, SplitType.EQUAL, inputs);

        assertEquals(3, splits.size());
        assertEquals(new BigDecimal("3.34"), splits.get(0).shareAmount());
        assertEquals(new BigDecimal("3.33"), splits.get(1).shareAmount());
        assertEquals(new BigDecimal("3.33"), splits.get(2).shareAmount());

        BigDecimal sum = splits.stream().map(SplitCalculationService.ComputedSplit::shareAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("10.00"), sum);
    }

    @Test
    @DisplayName("PERCENTAGE split: valid percentages calculated and remainder absorbed by last member")
    void testPercentageSplitValid() {
        BigDecimal total = new BigDecimal("100.00");
        List<SplitInput> inputs = List.of(
                new SplitInput(1L, new BigDecimal("50")),
                new SplitInput(2L, new BigDecimal("30")),
                new SplitInput(3L, new BigDecimal("20"))
        );

        List<SplitCalculationService.ComputedSplit> splits =
                splitCalculationService.compute(total, SplitType.PERCENTAGE, inputs);

        assertEquals(3, splits.size());
        assertEquals(new BigDecimal("50.00"), splits.get(0).shareAmount());
        assertEquals(new BigDecimal("30.00"), splits.get(1).shareAmount());
        assertEquals(new BigDecimal("20.00"), splits.get(2).shareAmount());
    }

    @Test
    @DisplayName("PERCENTAGE split: throws 400 Bad Request if percentages do not sum to 100")
    void testPercentageSplitInvalidSum() {
        BigDecimal total = new BigDecimal("100.00");
        List<SplitInput> inputs = List.of(
                new SplitInput(1L, new BigDecimal("50")),
                new SplitInput(2L, new BigDecimal("40")) // Sum = 90
        );

        assertThrows(ResponseStatusException.class, () ->
                splitCalculationService.compute(total, SplitType.PERCENTAGE, inputs));
    }

    @Test
    @DisplayName("EXACT split: valid exact amounts sum up to expense total")
    void testExactSplitValid() {
        BigDecimal total = new BigDecimal("45.50");
        List<SplitInput> inputs = List.of(
                new SplitInput(1L, new BigDecimal("20.50")),
                new SplitInput(2L, new BigDecimal("25.00"))
        );

        List<SplitCalculationService.ComputedSplit> splits =
                splitCalculationService.compute(total, SplitType.EXACT, inputs);

        assertEquals(2, splits.size());
        assertEquals(new BigDecimal("20.50"), splits.get(0).shareAmount());
        assertEquals(new BigDecimal("25.00"), splits.get(1).shareAmount());
    }

    @Test
    @DisplayName("EXACT split: throws 400 Bad Request if amounts do not match total")
    void testExactSplitInvalidSum() {
        BigDecimal total = new BigDecimal("50.00");
        List<SplitInput> inputs = List.of(
                new SplitInput(1L, new BigDecimal("20.00")),
                new SplitInput(2L, new BigDecimal("20.00")) // Sum = 40 != 50
        );

        assertThrows(ResponseStatusException.class, () ->
                splitCalculationService.compute(total, SplitType.EXACT, inputs));
    }
}
