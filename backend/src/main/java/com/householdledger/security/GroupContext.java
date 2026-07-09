package com.householdledger.security;

import com.householdledger.entity.GroupRole;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.stereotype.Component;

/**
 * Holds the "active tenant" for the duration of a single HTTP request.
 *
 * This is what makes tenancy here different from classic single-tenant-per-request
 * setups: a user can belong to many groups, and which one is "active" is a
 * per-request concept, resolved and verified by GroupContextFilter - never trusted
 * directly from client input beyond that one verification point.
 *
 * @RequestScope defaults to a CGLIB proxy (proxyMode = TARGET_CLASS), so this can be
 * safely injected into singleton-scoped services/controllers.
 */
@Component
@RequestScope
public class GroupContext {

    private Long groupId;
    private GroupRole role;

    public Long getGroupId(){
        if(groupId == null){
            throw new IllegalStateException("GroupContext accessed before being resolved - this endpoint must be under /api/groups/{groupId}/**");
        }
        return groupId;
    }
    public GroupRole getRole(){
        return role;
    }
    public void set(Long groupId, GroupRole role){
        this.groupId = groupId;
        this.role = role;
    }
}
