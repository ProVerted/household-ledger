package com.householdledger.service;

import com.householdledger.entity.Notification;
import com.householdledger.entity.User;
import com.householdledger.event.NotificationEvent;
import com.householdledger.repository.NotificationRepository;
import com.householdledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        notificationRepository.save(Notification.builder()
                .user(user)
                .message(event.getMessage())
                .build());
    }
}