package com.householdledger.repository;

import com.householdledger.entity.RecurringExpenseTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecurringExpenseTemplateRepository extends JpaRepository<RecurringExpenseTemplate, Long> {
    List<RecurringExpenseTemplate> findByActiveTrue();
    List<RecurringExpenseTemplate> findByGroupIdOrderByCreatedAtDesc(Long groupId);
}