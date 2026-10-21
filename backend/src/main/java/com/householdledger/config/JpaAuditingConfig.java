package com.householdledger.config;

import com.householdledger.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /**
     * Backs @CreatedBy/@LastModifiedBy on AbstractAuditableEntity. Reuses
     * the same SecurityUtils.getCurrentUserId() the rest of the app already
     * calls - one source of truth for "who is the current user", not a
     * second one bolted on just for auditing.
     *
     * Falls back to empty (no auditor) for contexts with no authenticated
     * user - e.g. the RecurringExpenseScheduler's @Scheduled job, which
     * runs outside a request and has no SecurityContext. Entities created
     * there (generated recurring expenses) will have a null createdBy
     * unless the scheduler explicitly sets one - see note in
     * RecurringExpenseGenerationService below.
     */
    @Bean
    public AuditorAware<Long> auditorAware() {
        return () -> {
            try {
                return Optional.of(SecurityUtils.getCurrentUserId());
            } catch (IllegalStateException ex) {
                return Optional.empty();
            }
        };
    }
}