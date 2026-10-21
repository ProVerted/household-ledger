package com.householdledger.security;

import com.householdledger.entity.Group;
import com.householdledger.entity.GroupMembership;
import com.householdledger.entity.GroupRole;
import com.householdledger.entity.User;
import com.householdledger.repository.GroupMembershipRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupContextFilterTest {

    @Mock
    private GroupMembershipRepository groupMembershipRepository;

    @Mock
    private GroupContext groupContext;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private GroupContextFilter groupContextFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Non-group subresource paths pass through filter without modification")
    void testNonSubresourcePathPassesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/groups/mine");
        MockHttpServletResponse response = new MockHttpServletResponse();

        groupContextFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(groupMembershipRepository);
        verifyNoInteractions(groupContext);
    }

    @Test
    @DisplayName("Subresource path with unauthenticated request yields 401 Unauthorized")
    void testUnauthenticatedSubresourceYields401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/groups/42/expenses");
        MockHttpServletResponse response = new MockHttpServletResponse();

        groupContextFilter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Subresource path for non-member yields 403 Forbidden")
    void testNonMemberSubresourceYields403() throws ServletException, IOException {
        User user = User.builder().id(99L).name("Intruder").email("intruder@example.com").build();
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/groups/42/expenses");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(groupMembershipRepository.findByGroupIdAndUserIdAndActiveTrue(42L, 99L))
                .thenReturn(Optional.empty());

        groupContextFilter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
        verify(groupContext, never()).set(any(), any());
    }

    @Test
    @DisplayName("Valid active member populates GroupContext and proceeds down the filter chain")
    void testActiveMemberSetsGroupContextAndProceeds() throws ServletException, IOException {
        User user = User.builder().id(1L).name("Member").email("member@example.com").build();
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/groups/42/expenses");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Group group = Group.builder().id(42L).name("Test Group").build();
        GroupMembership membership = GroupMembership.builder()
                .id(1L)
                .group(group)
                .user(user)
                .role(GroupRole.TREASURER)
                .active(true)
                .build();

        when(groupMembershipRepository.findByGroupIdAndUserIdAndActiveTrue(42L, 1L))
                .thenReturn(Optional.of(membership));

        groupContextFilter.doFilterInternal(request, response, filterChain);

        verify(groupContext).set(42L, GroupRole.TREASURER);
        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }
}
