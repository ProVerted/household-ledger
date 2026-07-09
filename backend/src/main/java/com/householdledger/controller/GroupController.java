package com.householdledger.controller;

import com.householdledger.dto.AddMemberRequest;
import com.householdledger.dto.CreateGroupRequest;
import com.householdledger.dto.GroupResponse;
import com.householdledger.dto.MembershipResponse;
import com.householdledger.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(request));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<GroupResponse>> getMyGroups() {
        return ResponseEntity.ok(groupService.getMyGroups());
    }

    // {groupId} below is consumed by GroupContextFilter before this method runs -
    // the filter already verified active membership. We don't re-read the path
    // variable here on purpose; GroupService pulls the verified id from GroupContext.
    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<MembershipResponse>> getMembers(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getMembers());
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<MembershipResponse> addMember(
            @PathVariable Long groupId,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.addMember(request));
    }

    @PatchMapping("/{groupId}/leave")
    public ResponseEntity<Void> leaveGroup(@PathVariable Long groupId) {
        groupService.leaveGroup();
        return ResponseEntity.noContent().build();
    }
}
