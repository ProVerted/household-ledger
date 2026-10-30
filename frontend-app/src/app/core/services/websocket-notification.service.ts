import { Injectable, inject, signal } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { AuthService } from './auth.service';
import { NotificationService } from './notification.service';
import { NotificationResponse } from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class WebsocketNotificationService {
  private authService = inject(AuthService);
  private notificationService = inject(NotificationService);

  private client: Client | null = null;
  readonly isConnected = signal(false);
  readonly latestNotification = signal<NotificationResponse | null>(null);

  private readonly notificationSubject = new Subject<NotificationResponse>();
  readonly onNotification$ = this.notificationSubject.asObservable();

  connect(): void {
    const token = this.authService.getAccessToken();
    const currentUser = this.authService.currentUser();
    if (!token || !currentUser || this.client?.active) {
      return;
    }

    // Connect directly via WebSocket STOMP
    const brokerUrl = 'ws://localhost:8080/ws';

    this.client = new Client({
      brokerURL: brokerUrl,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.isConnected.set(true);

        const handleIncoming = (message: IMessage) => {
          try {
            const notification: NotificationResponse = JSON.parse(message.body);
            this.latestNotification.set(notification);
            this.notificationSubject.next(notification);
            this.notificationService.incrementUnreadCount();
          } catch (e) {
            console.error('Failed to parse STOMP notification payload', e);
          }
        };

        // 1. Subscribe to private user queue
        this.client?.subscribe('/user/queue/notifications', handleIncoming);

        // 2. Subscribe to user-specific topic
        this.client?.subscribe(`/topic/users/${currentUser.userId}/notifications`, handleIncoming);
      },
      onDisconnect: () => {
        this.isConnected.set(false);
      },
      onStompError: (frame) => {
        console.warn('STOMP broker reported error: ' + frame.headers['message']);
        this.isConnected.set(false);
      },
      onWebSocketClose: () => {
        this.isConnected.set(false);
      },
    });

    try {
      this.client.activate();
    } catch (err) {
      console.warn('Failed to activate WebSocket client', err);
    }
  }

  disconnect(): void {
    if (this.client) {
      try {
        this.client.deactivate();
      } catch (err) {
        console.warn('Error during WebSocket disconnect', err);
      }
      this.client = null;
      this.isConnected.set(false);
    }
  }
}
