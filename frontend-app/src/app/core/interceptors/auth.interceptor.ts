import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Attaches the access token to every outgoing request (except auth endpoints,
 * which don't need one) and transparently retries once with a refreshed
 * token if the server responds 401.
 */
const RETRY_HEADER = 'X-Retry-Attempt';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const isAuthEndpoint = req.url.includes('/api/auth/');
  const isRetry = req.headers.has(RETRY_HEADER);

  const token = authService.getAccessToken();
  const authorizedReq =
    token && !isAuthEndpoint
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(authorizedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !isAuthEndpoint && !isRetry) {
        return authService.refresh().pipe(
          switchMap(() => {
            const refreshedToken = authService.getAccessToken();
            const retriedReq = req.clone({
              setHeaders: { Authorization: `Bearer ${refreshedToken}`,
                [RETRY_HEADER]: 'true',},
            });
            return next(retriedReq);
          }),
          catchError((refreshError) => {
            authService.logout();
            return throwError(() => refreshError);
          })
        );
      }
      return throwError(() => error);
      if (error.status === 401 && isRetry) {
        authService.logout();
      }
    })
  );
};
