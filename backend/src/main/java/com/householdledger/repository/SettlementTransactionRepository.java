package com.householdledger.repository;

import com.householdledger.entity.SettlementTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementTransactionRepository extends JpaRepository<SettlementTransaction, Long> {

    List<SettlementTransaction> findByGroupIdOrderBySettledAtDesc(Long groupId);
}
