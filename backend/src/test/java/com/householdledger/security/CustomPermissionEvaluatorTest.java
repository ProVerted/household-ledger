package com.householdledger.security;

import com.householdledger.entity.Group;
import com.householdledger.entity.GroupMembership;
import com.householdledger.entity.GroupRole;
import com.householdledger.entity.User;
import com.householdledger.repository.GroupMembershipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomPermissionEvaluatorTest {

    @Mock
    private GroupMembershipRepository groupMembershipRepository;

    @InjectMocks
    private CustomPermissionEvaluator permissionEvaluator;

    @Test
    @DisplayName("OWNER has APPROVE_EXPENSE and MANAGE_GROUP permissions")
    void testOwnerPermissions() {
        Long groupId = 100L;
        Long userId = 1L;

        User user = User.builder().id(userId).name("Owner").email("owner@test.com").build();
        UserPrincipal principal = new UserPrincipal(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        GroupMembership membership = GroupMembership.builder()
                .group(Group.builder().id(groupId).build())
                .user(user)
                .role(GroupRole.OWNER)
                .active(true)
                .build();

        when(groupMembershipRepository.findByGroupIdAndUserIdAndActiveTrue(groupId, userId))
                .thenReturn(Optional.of(membership));

        assertTrue(permissionEvaluator.hasPermission(auth, groupId, "group", "APPROVE_EXPENSE"));
        assertTrue(permissionEvaluator.hasPermission(auth, groupId, "group", "MANAGE_GROUP"));
        assertTrue(permissionEvaluator.hasPermission(auth, groupId, "group", "MANAGE_MEMBERS"));
    }

    @Test
    @DisplayName("TREASURER has APPROVE_EXPENSE but NOT MANAGE_GROUP permission")
    void testTreasurerPermissions() {
        Long groupId = 100L;
        Long userId = 2L;

        User user = User.builder().id(userId).name("Treasurer").email("treasurer@test.com").build();
        UserPrincipal principal = new UserPrincipal(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        GroupMembership membership = GroupMembership.builder()
                .group(Group.builder().id(groupId).build())
                .user(user)
                .role(GroupRole.TREASURER)
                .active(true)
                .build();

        when(groupMembershipRepository.findByGroupIdAndUserIdAndActiveTrue(groupId, userId))
                .thenReturn(Optional.of(membership));

        assertTrue(permissionEvaluator.hasPermission(auth, groupId, "group", "APPROVE_EXPENSE"));
        assertTrue(permissionEvaluator.hasPermission(auth, groupId, "group", "MANAGE_RECURRING"));
        assertFalse(permissionEvaluator.hasPermission(auth, groupId, "group", "MANAGE_GROUP"));
    }

    @Test
    @DisplayName("MEMBER has neither APPROVE_EXPENSE nor MANAGE_GROUP permissions")
    void testMemberPermissions() {
        Long groupId = 100L;
        Long userId = 3L;

        User user = User.builder().id(userId).name("Regular Member").email("member@test.com").build();
        UserPrincipal principal = new UserPrincipal(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        GroupMembership membership = GroupMembership.builder()
                .group(Group.builder().id(groupId).build())
                .user(user)
                .role(GroupRole.MEMBER)
                .active(true)
                .build();

        when(groupMembershipRepository.findByGroupIdAndUserIdAndActiveTrue(groupId, userId))
                .thenReturn(Optional.of(membership));

        assertFalse(permissionEvaluator.hasPermission(auth, groupId, "group", "APPROVE_EXPENSE"));
        assertFalse(permissionEvaluator.hasPermission(auth, groupId, "group", "MANAGE_GROUP"));
    }

    @Test
    @DisplayName("Unknown or non-group targets return false safely")
    void testNonGroupTargetReturnsFalse() {
        User user = User.builder().id(1L).name("User").email("user@test.com").build();
        UserPrincipal principal = new UserPrincipal(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        assertFalse(permissionEvaluator.hasPermission(auth, 100L, "document", "APPROVE_EXPENSE"));
    }
}
