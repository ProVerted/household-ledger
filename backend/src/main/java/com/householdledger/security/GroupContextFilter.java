package com.householdledger.security;

import com.householdledger.entity.GroupMembership;
import com.householdledger.repository.GroupMembershipRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves which group a request is acting against from the URL path
 * (e.g. /api/groups/42/expenses -> groupId = 42), then verifies the
 * authenticated user has an ACTIVE membership in that group before letting
 * the request proceed. This is what stops a user from supplying an arbitrary
 * groupId in the URL and reaching another group's data.
 *
 * Runs after JwtAuthFilter, since it needs SecurityContextHolder already populated.
 * Requests that don't match /api/groups/{id}/... (e.g. POST /api/groups, GET /api/groups/mine)
 * pass through untouched - they don't operate against a specific existing group yet.
 */
@Component
@RequiredArgsConstructor
public class GroupContextFilter extends OncePerRequestFilter{
    private static final Pattern GROUP_SUBRESOURCE_PATTERN = Pattern.compile("^/api/groups/(\\d+)/.+$");
    private final GroupMembershipRepository groupMembershipRepository;
    private final GroupContext groupContext;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        Matcher matcher = GROUP_SUBRESOURCE_PATTERN.matcher(request.getRequestURI());

        if(!matcher.matches()){
            filterChain.doFilter(request,response);
            return;
        }
        Long groupId = Long.valueOf(matcher.group(1));
        // Only reached if JwtAuthFilter already authenticated the request -
        // SecurityConfig requires authentication on everything except /api/auth/**.
        if(SecurityContextHolder.getContext().getAuthentication() == null){
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,"Authentication required");
            return;
        }
        Long userId = SecurityUtils.getCurrentUserId();
        Optional<GroupMembership> membership = groupMembershipRepository.findByGroupIdAndUserIdAndActiveTrue(groupId,userId);
        if(membership.isPresent()){
            // Deliberately vague: don't reveal whether the group exists at all
            // to a user who isn't a member of it.
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not a member of this group");
            return;
        }
        groupContext.set(groupId,membership.get().getRole());
        filterChain.doFilter(request,response);
    }
}
