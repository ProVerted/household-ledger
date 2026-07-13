package com.householdledger.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
@Table(name = "app_group")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // Expenses at or below this amount are auto-approved; above it, they land
    // in PENDING_APPROVAL until a treasurer/owner signs off. Defaulted at
    // creation time so every group has a sensible value without the creator
    // having to think about it up front.
    @Column(name = "approval_threshold", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal approvalThreshold = new BigDecimal("100.00");

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
