import { TestBed } from '@angular/core/testing';
import { WebsocketNotificationService } from './websocket-notification.service';
import { AuthService } from './auth.service';
import { NotificationService } from './notification.service';
import { NotificationResponse } from '../models/notification.model';

describe('WebsocketNotificationService', () => {
  let service: WebsocketNotificationService;
  let authServiceSpy: { getAccessToken: ReturnType<typeof vi.fn>; currentUser: ReturnType<typeof vi.fn> };
  let notificationServiceSpy: { incrementUnreadCount: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authServiceSpy = {
      getAccessToken: vi.fn().mockReturnValue('mock-jwt-token'),
      currentUser: vi.fn().mockReturnValue({ userId: 1, name: 'Alice', email: 'alice@test.com' }),
    };
    notificationServiceSpy = {
      incrementUnreadCount: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        WebsocketNotificationService,
        { provide: AuthService, useValue: authServiceSpy },
        { provide: NotificationService, useValue: notificationServiceSpy },
      ],
    });

    service = TestBed.inject(WebsocketNotificationService);
  });

  it('should initialize with disconnected state and no latest notification', () => {
    expect(service.isConnected()).toBe(false);
    expect(service.latestNotification()).toBeNull();
  });

  it('should disconnect cleanly and reset connection state', () => {
    service.disconnect();
    expect(service.isConnected()).toBe(false);
  });

  it('should not connect if user is not authenticated', () => {
    authServiceSpy.getAccessToken.mockReturnValue(null);
    service.connect();
    expect(service.isConnected()).toBe(false);
  });
});
