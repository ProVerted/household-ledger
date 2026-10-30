import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { GroupContextService } from '../../core/services/group-context.service';
import { GroupService } from '../../core/services/group.service';
import { NotificationService } from '../../core/services/notification.service';
import { WebsocketNotificationService } from '../../core/services/websocket-notification.service';
import { NotificationResponse } from '../../core/models/notification.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="dashboard">
      <div class="dashboard-header">
        <h1>Welcome, {{ authService.currentUser()?.name }}</h1>

        <div style="display: flex; align-items: center; gap: 10px;">
          @if (wsService.isConnected()) {
            <span
              title="Live Real-Time WebSocket Connected"
              style="font-size: 0.75rem; color: #2f9e44; display: flex; align-items: center; gap: 4px;"
            >
              <span style="display: inline-block; width: 8px; height: 8px; background: #2f9e44; border-radius: 50%;"></span>
              Live
            </span>
          }

          <button class="notification-bell" (click)="toggleNotifications()">
            🔔
            @if (notificationService.unreadCount() > 0) {
              <span class="badge">{{ notificationService.unreadCount() }}</span>
            }
          </button>
        </div>
      </div>

      @if (latestLiveAlert(); as alert) {
        <div
          class="live-toast"
          style="margin-bottom: 15px; padding: 10px 14px; background: #eef7f2; border-left: 4px solid #1f6f5c; border-radius: 4px; display: flex; justify-content: space-between; align-items: center;"
        >
          <span>⚡ <strong>Just now:</strong> {{ alert.message }}</span>
          <button
            type="button"
            (click)="dismissLiveAlert()"
            style="background: transparent; border: none; font-size: 1rem; cursor: pointer; color: #666;"
          >
            ✕
          </button>
        </div>
      }

      @if (showNotifications()) {
        <div class="notification-panel">
          @if (notifications().length === 0) {
            <p>No notifications yet.</p>
          } @else {
            @for (n of notifications(); track n.id) {
              <p [class.unread]="!n.read" (click)="markRead(n)">{{ n.message }}</p>
            }
          }
        </div>
      }

      @if (groupContext.activeGroup(); as active) {
        <p>
          Active group: <strong>{{ active.name }}</strong>
          ({{ active.type }} · {{ active.currency }} · your role: {{ active.myRole }})
        </p>
      } @else {
        <p>You don't have an active group yet.</p>
      }

      <p><a routerLink="/groups">Manage / switch groups</a></p>
      <p><a routerLink="/expenses">View expenses</a></p>
      <p><a routerLink="/balances">Balances &amp; Settlements</a></p>
      <p><a routerLink="/recurring">Recurring expenses</a></p>

      <button (click)="logout()">Log Out</button>
    </div>
  `,
})
export class DashboardComponent implements OnInit, OnDestroy {
  authService = inject(AuthService);
  groupContext = inject(GroupContextService);
  notificationService = inject(NotificationService);
  wsService = inject(WebsocketNotificationService);
  private groupService = inject(GroupService);
  private router = inject(Router);

  readonly showNotifications = signal(false);
  readonly notifications = signal<NotificationResponse[]>([]);
  readonly latestLiveAlert = signal<NotificationResponse | null>(null);

  private liveSub: Subscription | null = null;
  private pollHandle: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.groupService.getMyGroups().subscribe({
      next: (groups) => this.groupContext.setGroups(groups),
      error: () => {},
    });

    this.notificationService.refreshUnreadCount();

    // Connect real-time STOMP WebSocket
    this.wsService.connect();

    // Listen to live push notifications
    this.liveSub = this.wsService.onNotification$.subscribe((notification) => {
      this.latestLiveAlert.set(notification);
      // Prepend to current notification list if open
      this.notifications.update((list) => [notification, ...list]);
    });

    // Background heartbeat polling fallback (every 60s) in case WebSocket temporarily drops
    this.pollHandle = setInterval(() => this.notificationService.refreshUnreadCount(), 60000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
    }
    if (this.liveSub) {
      this.liveSub.unsubscribe();
    }
  }

  dismissLiveAlert(): void {
    this.latestLiveAlert.set(null);
  }

  toggleNotifications(): void {
    this.showNotifications.update((v) => !v);
    if (this.showNotifications()) {
      this.notificationService.getMyNotifications().subscribe({
        next: (list) => this.notifications.set(list),
        error: () => {},
      });
    }
  }

  markRead(notification: NotificationResponse): void {
    if (notification.read) return;
    this.notificationService.markRead(notification.id).subscribe({
      next: () => {
        notification.read = true;
        this.notifications.update((list) => [...list]);
      },
    });
  }

  logout(): void {
    this.wsService.disconnect();
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
