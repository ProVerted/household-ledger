package com.householdledger.service;

import com.householdledger.dto.NotificationResponse;
import com.householdledger.entity.Notification;
import com.householdledger.entity.User;
import com.householdledger.event.NotificationEvent;
import com.householdledger.repository.NotificationRepository;
import com.householdledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // @TransactionalEventListener(phase = AFTER_COMMIT), not a plain
    // @EventListener: NotificationEvent is usually published from inside
    // another @Transactional method (e.g. ExpenseService.createExpense). If we
    // reacted immediately, a later rollback of that surrounding transaction
    // would still leave a notification behind for something that never
    // actually happened. Waiting for AFTER_COMMIT means "only notify once
    // we're certain the thing being notified about really occurred."
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationEvent(NotificationEvent event) {
        User user = userRepository.findById(event.getUserId()).orElse(null);
        if (user == null) {
            log.warn("Notification event for unknown user {}: {}", event.getUserId(), event.getMessage());
            return;
        }

        Notification saved = notificationRepository.save(Notification.builder()
                .user(user)
                .message(event.getMessage())
                .build());

        NotificationResponse response = NotificationResponse.builder()
                .id(saved.getId())
                .message(saved.getMessage())
                .read(saved.isRead())
                .createdAt(saved.getCreatedAt())
                .build();

        // 1. Real-time push to the user's private queue (/user/{userId}/queue/notifications)
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(event.getUserId()),
                    "/queue/notifications",
                    response
            );

            // 2. Also broadcast to user-specific topic for clients subscribing to direct topics
            messagingTemplate.convertAndSend(
                    "/topic/users/" + event.getUserId() + "/notifications",
                    response
            );

            log.debug("Pushed live STOMP notification to user {}: {}", event.getUserId(), event.getMessage());
        } catch (Exception e) {
            log.warn("Failed to dispatch live WebSocket notification to user {}: {}", event.getUserId(), e.getMessage());
        }
    }
}
