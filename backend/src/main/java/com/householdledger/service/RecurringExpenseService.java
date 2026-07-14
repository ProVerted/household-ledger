package com.householdledger.service;

import com.householdledger.dto.CreateRecurringExpenseRequest;
import com.householdledger.dto.RecurringExpenseResponse;
import com.householdledger.entity.Group;
import com.householdledger.entity.RecurringExpenseTemplate;
import com.householdledger.entity.User;
import com.householdledger.repository.GroupMembershipRepository;
import com.householdledger.repository.GroupRepository;
import com.householdledger.repository.RecurringExpenseTemplateRepository;
import com.householdledger.repository.UserRepository;
import com.householdledger.security.GroupContext;
import com.householdledger.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Handles the CRUD side of recurring templates (what a user sets up from the
 * UI). The actual monthly generation is a separate concern, split across
 * RecurringExpenseScheduler and RecurringExpenseGenerationService - see the
 * javadoc on RecurringExpenseScheduler for why that's not just another method
 * on this class.
 */
@Service
@RequiredArgsConstructor
public class RecurringExpenseService {

    private final RecurringExpenseTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final GroupContext groupContext;

    @Transactional
    public RecurringExpenseResponse createTemplate(CreateRecurringExpenseRequest request) {
        Long groupId = groupContext.getGroupId();
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long paidByUserId = request.getPaidByUserId() != null ? request.getPaidByUserId() : currentUserId;

        if (!membershipRepository.existsByGroupIdAndUserIdAndActiveTrue(groupId, paidByUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "paidByUserId is not an active member of this group");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
        User paidBy = userRepository.findById(paidByUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "paidByUserId not found"));

        RecurringExpenseTemplate template = RecurringExpenseTemplate.builder()
                .group(group)
                .description(request.getDescription())
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .category(request.getCategory())
                .paidBy(paidBy)
                .dayOfMonth(request.getDayOfMonth())
                .build();

        return toResponse(templateRepository.save(template));
    }

    public List<RecurringExpenseResponse> getTemplates() {
        Long groupId = groupContext.getGroupId();
        return templateRepository.findByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deactivate(Long templateId) {
        Long groupId = groupContext.getGroupId();
        RecurringExpenseTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring template not found"));

        if (!template.getGroup().getId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring template not found");
        }
        template.setActive(false);
        templateRepository.save(template);
    }

    private RecurringExpenseResponse toResponse(RecurringExpenseTemplate t) {
        return RecurringExpenseResponse.builder()
                .id(t.getId())
                .description(t.getDescription())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .category(t.getCategory())
                .dayOfMonth(t.getDayOfMonth())
                .active(t.isActive())
                .paidByUserId(t.getPaidBy().getId())
                .paidByName(t.getPaidBy().getName())
                .lastGeneratedAt(t.getLastGeneratedAt())
                .build();
    }
}