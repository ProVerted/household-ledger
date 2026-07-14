import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { GroupContextService } from '../../core/services/group-context.service';
import { GroupService } from '../../core/services/group.service';
import { NotificationService } from '../../core/services/notification.service';
import { NotificationResponse } from '../../core/models/notification.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="dashboard">
      <div class="dashboard-header">
        <h1>Welcome, {{ authService.currentUser()?.name }}</h1>

        <button class="notification-bell" (click)="toggleNotifications()">
          🔔
          @if (notificationService.unreadCount() > 0) {
            <span class="badge">{{ notificationService.unreadCount() }}</span>
          }
        </button>
      </div>

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
  private groupService = inject(GroupService);
  private router = inject(Router);

  readonly showNotifications = signal(false);
  readonly notifications = signal<NotificationResponse[]>([]);

  // Polling rather than WebSockets for now - the spec calls out live
  // WebSocket updates as an optional stretch goal on top of this, not a
  // requirement; this gets the notification badge working without that
  // added complexity.
  private pollHandle: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.groupService.getMyGroups().subscribe({
      next: (groups) => this.groupContext.setGroups(groups),
      error: () => {
        // Non-fatal for the dashboard - the groups page will surface the real error.
      },
    });

    this.notificationService.refreshUnreadCount();
    this.pollHandle = setInterval(() => this.notificationService.refreshUnreadCount(), 30000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) {
      clearInterval(this.pollHandle);
    }
  }

  toggleNotifications(): void {
    this.showNotifications.update((v) => !v);
    if (this.showNotifications()) {
      this.notificationService.getMyNotifications().subscribe({
        next: (list) => this.notifications.set(list),
        error: () => {
          // Non-fatal - the bell icon's count already came from refreshUnreadCount().
        },
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
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}