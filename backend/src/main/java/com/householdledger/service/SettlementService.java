package com.householdledger.service;

import com.householdledger.dto.*;
import com.householdledger.entity.Group;
import com.householdledger.entity.GroupMembership;
import com.householdledger.entity.SettlementTransaction;
import com.householdledger.entity.User;
import com.householdledger.repository.GroupMembershipRepository;
import com.householdledger.repository.SettlementTransactionRepository;
import com.householdledger.repository.UserRepository;
import com.householdledger.security.GroupContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final BalanceService balanceService;
    private final SettlementCalculationService settlementCalculationService;
    private final GroupMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final SettlementTransactionRepository settlementTransactionRepository;
    private final GroupContext groupContext;

    public List<BalanceResponse> getBalances() {
        return balanceService.getBalances(groupContext.getGroupId());
    }

    public List<SettlementSuggestionResponse> getSuggestedSettlements() {
        Long groupId = groupContext.getGroupId();
        Map<Long, BigDecimal> balances = balanceService.computeNetBalances(groupId);

        Map<Long, String> names = new LinkedHashMap<>();
        for (GroupMembership m : membershipRepository.findByGroupIdAndActiveTrue(groupId)) {
            names.put(m.getUser().getId(), m.getUser().getName());
        }

        return settlementCalculationService.suggestSettlements(balances, names);
    }

    @Transactional
    public SettlementTransactionResponse confirmSettlement(ConfirmSettlementRequest request) {
        Long groupId = groupContext.getGroupId();

        User fromUser = userRepository.findById(request.getFromUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "fromUserId not found"));
        User toUser = userRepository.findById(request.getToUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "toUserId not found"));

        if (!membershipRepository.existsByGroupIdAndUserIdAndActiveTrue(groupId, fromUser.getId())
                || !membershipRepository.existsByGroupIdAndUserIdAndActiveTrue(groupId, toUser.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both users must be active members of this group");
        }

        Group group = new Group();
        group.setId(groupId);

        SettlementTransaction transaction = SettlementTransaction.builder()
                .group(group)
                .fromUser(fromUser)
                .toUser(toUser)
                .amount(request.getAmount())
                .build();

        transaction = settlementTransactionRepository.save(transaction);

        return SettlementTransactionResponse.builder()
                .id(transaction.getId())
                .fromUserId(fromUser.getId())
                .fromName(fromUser.getName())
                .toUserId(toUser.getId())
                .toName(toUser.getName())
                .amount(transaction.getAmount())
                .settledAt(transaction.getSettledAt())
                .build();
    }

    public List<SettlementTransactionResponse> getSettlementHistory() {
        Long groupId = groupContext.getGroupId();
        return settlementTransactionRepository.findByGroupIdOrderBySettledAtDesc(groupId).stream()
                .map(t -> SettlementTransactionResponse.builder()
                        .id(t.getId())
                        .fromUserId(t.getFromUser().getId())
                        .fromName(t.getFromUser().getName())
                        .toUserId(t.getToUser().getId())
                        .toName(t.getToUser().getName())
                        .amount(t.getAmount())
                        .settledAt(t.getSettledAt())
                        .build())
                .toList();
    }
}
