package com.householdledger.repository;

import com.householdledger.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByGroupIdOrderByExpenseDateDesc(Long groupId);
}