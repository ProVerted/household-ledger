package com.householdledger.event;

import lombok.Getter;

/**
 * Published whenever something happens that a user should be told about (an
 * expense needing their approval, a decision on an expense they submitted, a
 * recurring bill firing). NotificationEventListener picks these up and turns
 * them into Notification rows.
 *
 * Deliberately decoupled from whatever triggers it: ExpenseService and
 * RecurringExpenseGenerationService don't know "notifications" exist as a
 * concept - they just publish "this happened," and this event is the only
 * contract between the two sides. Adding a second notification channel later
 * (email, push) means adding a second listener, not touching those services
 * again.
 */
@Getter
public class NotificationEvent {
    private final Long userId;
    private final String message;

    public NotificationEvent(Long userId, String message) {
        this.userId = userId;
        this.message = message;
    }
}