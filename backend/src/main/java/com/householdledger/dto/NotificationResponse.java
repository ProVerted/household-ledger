package com.householdledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private String message;
    private boolean read;
    private Instant createdAt;
}