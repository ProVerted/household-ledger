package com.householdledger.service;

import com.householdledger.dto.SplitInput;
import com.householdledger.entity.SplitType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class SplitCalculationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal CENT = new BigDecimal("0.01");
    // Small tolerance for comparing sums, since split inputs arrive as decimals
    // from JSON and shouldn't be compared for exact equality.
    private static final BigDecimal TOLERANCE = new BigDecimal("0.02");

    public record ComputedSplit(Long userId, BigDecimal shareAmount, BigDecimal sharePercentage) {}

    public List<ComputedSplit> compute(BigDecimal totalAmount, SplitType splitType, List<SplitInput> inputs) {
        return switch (splitType) {
            case EQUAL -> computeEqual(totalAmount, inputs);
            case PERCENTAGE -> computePercentage(totalAmount, inputs);
            case EXACT -> computeExact(totalAmount, inputs);
        };
    }

    private List<ComputedSplit> computeEqual(BigDecimal totalAmount, List<SplitInput> inputs) {
        int n = inputs.size();
        BigDecimal baseShare = totalAmount.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        BigDecimal distributed = baseShare.multiply(BigDecimal.valueOf(n));
        BigDecimal remainder = totalAmount.subtract(distributed);
        int remainderCents = remainder.divide(CENT, 0, RoundingMode.HALF_UP).intValue();

        List<ComputedSplit> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            // Distribute the leftover cents (from integer division) one-by-one
            // across the first N people, so the splits always sum exactly to
            // totalAmount instead of being off by a cent or two.
            BigDecimal share = i < remainderCents ? baseShare.add(CENT) : baseShare;
            result.add(new ComputedSplit(inputs.get(i).getUserId(), share, null));
        }
        return result;
    }

    private List<ComputedSplit> computePercentage(BigDecimal totalAmount, List<SplitInput> inputs) {
        BigDecimal percentageSum = inputs.stream()
                .map(SplitInput::getValue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (percentageSum.subtract(HUNDRED).abs().compareTo(TOLERANCE) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Percentage splits must sum to 100 (got " + percentageSum + ")");
        }

        List<ComputedSplit> result = new ArrayList<>();
        BigDecimal runningTotal = BigDecimal.ZERO;

        for (int i = 0; i < inputs.size(); i++) {
            SplitInput input = inputs.get(i);
            BigDecimal percentage = input.getValue() != null ? input.getValue() : BigDecimal.ZERO;
            BigDecimal share;

            if (i == inputs.size() - 1) {
                // Last split absorbs any rounding difference so the total is exact,
                // rather than every split independently rounding and drifting off.
                share = totalAmount.subtract(runningTotal).setScale(2, RoundingMode.HALF_UP);
            } else {
                share = totalAmount.multiply(percentage)
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                runningTotal = runningTotal.add(share);
            }

            result.add(new ComputedSplit(input.getUserId(), share, percentage));
        }
        return result;
    }

    private List<ComputedSplit> computeExact(BigDecimal totalAmount, List<SplitInput> inputs) {
        BigDecimal sum = inputs.stream()
                .map(SplitInput::getValue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.subtract(totalAmount).abs().compareTo(TOLERANCE) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Exact splits must sum to the expense total (" + totalAmount + "), got " + sum);
        }

        List<ComputedSplit> result = new ArrayList<>();
        for (SplitInput input : inputs) {
            BigDecimal share = input.getValue() != null
                    ? input.getValue().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            result.add(new ComputedSplit(input.getUserId(), share, null));
        }
        return result;
    }
}
