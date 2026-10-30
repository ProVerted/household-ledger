import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { NotificationResponse } from '../models/notification.model';

/**
 * Notifications aren't scoped to a group - a user sees them across every
 * group they belong to - so this deliberately doesn't take a groupId anywhere,
 * matching the backend's NotificationController living outside /api/groups/**.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly baseUrl = 'http://localhost:8080/api/notifications';

  // Shared across the app: the badge component reads this reactively, and
  // anything that changes notification state (loading the list, marking one
  // read) keeps it in sync via refreshUnreadCount() below, rather than each
  // component polling independently.
  private readonly unreadCountSignal = signal(0);
  readonly unreadCount = this.unreadCountSignal.asReadonly();

  constructor(private http: HttpClient) {}

  getMyNotifications(): Observable<NotificationResponse[]> {
    return this.http.get<NotificationResponse[]>(this.baseUrl);
  }

  refreshUnreadCount(): void {
    this.http.get<{ unreadCount: number }>(`${this.baseUrl}/unread-count`).subscribe({
      next: (res) => this.unreadCountSignal.set(res.unreadCount),
      error: () => {
        // Non-fatal - the badge just won't update this cycle, next poll retries.
      },
    });
  }

  incrementUnreadCount(): void {
    this.unreadCountSignal.update((count) => count + 1);
  }

  markRead(notificationId: number): Observable<void> {
    return this.http
      .patch<void>(`${this.baseUrl}/${notificationId}/read`, {})
      .pipe(tap(() => this.refreshUnreadCount()));
  }
}
