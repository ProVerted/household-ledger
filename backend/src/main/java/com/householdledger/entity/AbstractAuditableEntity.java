package com.householdledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Every persistent entity should extend this instead of declaring its own
 * createdAt/createdBy fields + @PrePersist hook. Spring Data JPA auditing
 * (enabled via @EnableJpaAuditing in JpaAuditingConfig, backed by
 * SpringSecurityAuditorAware) fills these in automatically on
 * persist/merge - no per-entity boilerplate, and no entity can forget to
 * set createdBy the way the original Expense entity did.
 *
 * Soft delete is included here too: repositories should filter on
 * `deleted = false` (or entities can override with a @Where clause) rather
 * than issuing a hard DELETE, so financial history is never actually lost.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AbstractAuditableEntity {

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;
}