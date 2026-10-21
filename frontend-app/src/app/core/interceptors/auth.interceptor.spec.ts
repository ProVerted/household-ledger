import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: { getAccessToken: ReturnType<typeof vi.fn>; refresh: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authServiceSpy = {
      getAccessToken: vi.fn(),
      refresh: vi.fn(),
      logout: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should attach Bearer token to authenticated non-auth requests', () => {
    authServiceSpy.getAccessToken.mockReturnValue('dummy-jwt-token');

    http.get('/api/groups/mine').subscribe();

    const req = httpMock.expectOne('/api/groups/mine');
    expect(req.request.headers.get('Authorization')).toBe('Bearer dummy-jwt-token');
    req.flush([]);
  });

  it('should NOT attach Bearer token to /api/auth/ endpoints', () => {
    authServiceSpy.getAccessToken.mockReturnValue('dummy-jwt-token');

    http.post('/api/auth/login', {}).subscribe();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
});
