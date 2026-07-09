package com.householdledger.dto;

import com.householdledger.entity.GroupRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MembershipResponse {
    private Long userId;
    private String name;
    private String email;
    private GroupRole role;
    private boolean active;
}
