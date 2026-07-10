package com.householdledger.service;

import com.householdledger.dto.SettlementSuggestionResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

@Service
public class SettlementCalculationService {

    private static final BigDecimal EPSILON = new BigDecimal("0.005");

    private record Ledger(Long userId, BigDecimal amount) {}

    /**
     * Greedy debt-minimization: repeatedly match the largest creditor with the
     * largest debtor and settle the smaller of the two amounts. This is the
     * well-known approach (sometimes called the "Splitwise algorithm") for
     * minimizing the number of transactions needed to settle a group's debts -
     * provably better than naively settling every pairwise debt individually.
     */
    public List<SettlementSuggestionResponse> suggestSettlements(
            Map<Long, BigDecimal> netBalances, Map<Long, String> names) {

        PriorityQueue<Ledger> creditors = new PriorityQueue<>((a, b) -> b.amount().compareTo(a.amount()));
        PriorityQueue<Ledger> debtors = new PriorityQueue<>((a, b) -> b.amount().compareTo(a.amount()));

        for (Map.Entry<Long, BigDecimal> entry : netBalances.entrySet()) {
            BigDecimal amount = entry.getValue();
            if (amount.compareTo(EPSILON) > 0) {
                creditors.add(new Ledger(entry.getKey(), amount));
            } else if (amount.compareTo(EPSILON.negate()) < 0) {
                debtors.add(new Ledger(entry.getKey(), amount.abs()));
            }
            // Balances within [-EPSILON, EPSILON] are already settled - skip.
        }

        List<SettlementSuggestionResponse> settlements = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Ledger topCreditor = creditors.poll();
            Ledger topDebtor = debtors.poll();

            BigDecimal settleAmount = topCreditor.amount().min(topDebtor.amount())
                    .setScale(2, RoundingMode.HALF_UP);

            if (settleAmount.compareTo(EPSILON) > 0) {
                settlements.add(SettlementSuggestionResponse.builder()
                        .fromUserId(topDebtor.userId())
                        .fromName(names.getOrDefault(topDebtor.userId(), "Unknown"))
                        .toUserId(topCreditor.userId())
                        .toName(names.getOrDefault(topCreditor.userId(), "Unknown"))
                        .amount(settleAmount)
                        .build());
            }

            BigDecimal creditorRemaining = topCreditor.amount().subtract(settleAmount);
            BigDecimal debtorRemaining = topDebtor.amount().subtract(settleAmount);

            // Whichever side still has money left over goes back in its queue
            // to be matched again in the next round; the side that hit exactly
            // zero is fully settled and drops out.
            if (creditorRemaining.compareTo(EPSILON) > 0) {
                creditors.add(new Ledger(topCreditor.userId(), creditorRemaining));
            }
            if (debtorRemaining.compareTo(EPSILON) > 0) {
                debtors.add(new Ledger(topDebtor.userId(), debtorRemaining));
            }
        }

        return settlements;
    }
}
