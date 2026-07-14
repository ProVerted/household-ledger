package com.householdledger.security;

import com.householdledger.entity.GroupMembership;
import com.householdledger.entity.GroupRole;
import com.householdledger.repository.GroupMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * Lets controller methods write:
 *
 *   @PreAuthorize("hasPermission(#groupId, 'group', 'APPROVE_EXPENSE')")
 *
 * instead of hardcoded role checks scattered across services. The permission
 * string is looked up against a small role map below - adding a new permission
 * or changing who can do what is a one-line change here, not a hunt through
 * every method that happens to care about roles.
 *
 * This coexists with GroupService's earlier manual requireRole() checks (from
 * Phase 2) rather than replacing them outright - both approaches are valid;
 * this is the more declarative version, worth using for new permission checks
 * going forward.
 */
@Component
@RequiredArgsConstructor
public class CustomPermissionEvaluator implements PermissionEvaluator{
    private static final Map<String, Set<GroupRole>> PERMISSION_ROLES = Map.of(
            "APPROVE_EXPENSE", Set.of(GroupRole.OWNER, GroupRole.TREASURER),
            "MANAGE_MEMBERS", Set.of(GroupRole.OWNER, GroupRole.TREASURER),
            "MANAGE_GROUP", Set.of(GroupRole.OWNER),
            "MANAGE_RECURRING", Set.of(GroupRole.OWNER, GroupRole.TREASURER)
    );

    private final GroupMembershipRepository groupMembershipRepository;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        // Not used in this project - every call site provides an explicit id,
        // going through the overload below instead.
        return false;
    }

    @Override
    public boolean hasPermission(
            Authentication authentication, Serializable targetId, String targetType, Object permission) {

        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        if (!"group".equals(targetType)) {
            return false;
        }

        Long groupId = Long.valueOf(targetId.toString());
        Long userId = principal.getId();

        GroupMembership membership = groupMembershipRepository
                .findByGroupIdAndUserIdAndActiveTrue(groupId, userId)
                .orElse(null);

        if (membership == null) {
            return false;
        }

        Set<GroupRole> allowedRoles = PERMISSION_ROLES.get(permission.toString());
        return allowedRoles != null && allowedRoles.contains(membership.getRole());
    }
}