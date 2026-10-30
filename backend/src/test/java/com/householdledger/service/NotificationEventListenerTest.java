package com.householdledger.service;

import com.householdledger.dto.NotificationResponse;
import com.householdledger.entity.Notification;
import com.householdledger.entity.User;
import com.householdledger.event.NotificationEvent;
import com.householdledger.repository.NotificationRepository;
import com.householdledger.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    @DisplayName("Saves notification and pushes live STOMP messages to user queue and topic")
    void testDispatchesLiveWebSocketNotification() {
        Long userId = 42L;
        String message = "Alice added an expense for Dinner";

        User user = User.builder().id(userId).name("Bob").email("bob@example.com").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Notification saved = Notification.builder()
                .id(100L)
                .user(user)
                .message(message)
                .read(false)
                .build();
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        NotificationEvent event = new NotificationEvent(userId, message);
        listener.onNotificationEvent(event);

        // Verify database persistence
        verify(notificationRepository).save(any(Notification.class));

        // Verify WebSocket push to user queue
        ArgumentCaptor<NotificationResponse> queueCaptor = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(String.valueOf(userId)),
                eq("/queue/notifications"),
                queueCaptor.capture()
        );
        assertEquals(message, queueCaptor.getValue().getMessage());
        assertEquals(100L, queueCaptor.getValue().getId());

        // Verify WebSocket broadcast to user topic
        verify(messagingTemplate).convertAndSend(
                eq("/topic/users/" + userId + "/notifications"),
                any(NotificationResponse.class)
        );
    }

    @Test
    @DisplayName("Ignores event when user is not found in database")
    void testUserNotFoundDoesNothing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        listener.onNotificationEvent(new NotificationEvent(99L, "Some message"));

        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(messagingTemplate);
    }
}
