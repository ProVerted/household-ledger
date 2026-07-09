package com.householdledger.repository;

import com.householdledger.entity.GroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership,Long> {
    Optional<GroupMembership>findByGroupIdAndUserIdAndActiveTrue(Long groupId, Long userId);
    List<GroupMembership> findByUserIdAndActiveTrue(Long userId);
    List<GroupMembership> findByGroupIdAndActiveTrue(Long groupId);
    boolean existsByGroupIdAndUserIdAndActiveTrue(Long groupId, Long userId);

}
