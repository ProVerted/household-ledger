package com.householdledger.service;

import com.householdledger.dto.AddMemberRequest;
import com.householdledger.dto.CreateGroupRequest;
import com.householdledger.dto.GroupResponse;
import com.householdledger.dto.MembershipResponse;
import com.householdledger.entity.Group;
import com.householdledger.entity.GroupMembership;
import com.householdledger.entity.GroupRole;
import com.householdledger.entity.User;
import com.householdledger.repository.GroupMembershipRepository;
import com.householdledger.repository.GroupRepository;
import com.householdledger.repository.UserRepository;
import com.householdledger.security.GroupContext;
import com.householdledger.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final GroupContext groupContext;

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        Group group = Group.builder()
                .name(request.getName())
                .type(request.getType())
                .currency(request.getCurrency().toUpperCase())
                .createdBy(currentUserId)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();
        group = groupRepository.save(group);

        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        GroupMembership ownerMembership = GroupMembership.builder()
                .group(group)
                .user(creator)
                .role(GroupRole.OWNER)
                .build();
        membershipRepository.save(ownerMembership);

        return toGroupResponse(group, GroupRole.OWNER);
    }

    public List<GroupResponse> getMyGroups() {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        return membershipRepository.findByUserIdAndActiveTrue(currentUserId).stream()
                .map(membership -> toGroupResponse(membership.getGroup(), membership.getRole()))
                .toList();
    }

    @Transactional
    public MembershipResponse addMember(AddMemberRequest request) {
        requireRole(GroupRole.OWNER, GroupRole.TREASURER);

        Long groupId = groupContext.getGroupId();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        User userToAdd = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No user with that email"));

        if (membershipRepository.existsByGroupIdAndUserIdAndActiveTrue(groupId, userToAdd.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this group");
        }

        GroupMembership membership = GroupMembership.builder()
                .group(group)
                .user(userToAdd)
                .role(GroupRole.MEMBER)
                .build();
        membership = membershipRepository.save(membership);

        return toMembershipResponse(membership);
    }

    public List<MembershipResponse> getMembers() {
        Long groupId = groupContext.getGroupId();
        return membershipRepository.findByGroupIdAndActiveTrue(groupId).stream()
                .map(this::toMembershipResponse)
                .toList();
    }

    @Transactional
    public void leaveGroup() {
        Long groupId = groupContext.getGroupId();
        Long currentUserId = SecurityUtils.getCurrentUserId();

        GroupMembership membership = membershipRepository.findByGroupIdAndUserIdAndActiveTrue(groupId, currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));

        if (membership.getRole() == GroupRole.OWNER) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The owner can't leave the group - transfer ownership first (planned for a later phase)");
        }

        // Deactivate rather than delete - preserves history for past expenses/splits.
        membership.setActive(false);
        membershipRepository.save(membership);
    }

    private void requireRole(GroupRole... allowedRoles) {
        GroupRole currentRole = groupContext.getRole();
        for (GroupRole allowed : allowedRoles) {
            if (currentRole == allowed) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have permission to do that in this group");
    }

    private GroupResponse toGroupResponse(Group group, GroupRole myRole) {
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .type(group.getType())
                .currency(group.getCurrency())
                .startDate(group.getStartDate())
                .endDate(group.getEndDate())
                .myRole(myRole)
                .build();
    }

    private MembershipResponse toMembershipResponse(GroupMembership membership) {
        return MembershipResponse.builder()
                .userId(membership.getUser().getId())
                .name(membership.getUser().getName())
                .email(membership.getUser().getEmail())
                .role(membership.getRole())
                .active(membership.isActive())
                .build();
    }
}
