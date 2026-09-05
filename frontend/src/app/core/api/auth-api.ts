import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, ChangePasswordRequest, LoginRequest, LoginResponse } from './models';
import { unwrap, unwrapVoid } from './unwrap';

/**
 * Stable error codes the auth endpoints return. Screens branch on these, never on `error.message`
 * — the message is copy that may be reworded or translated, the code is the contract (ADR-0007).
 */
export const AUTH_ERROR = {
  /** 401. Wrong username *or* wrong password — the backend deliberately does not say which. */
  INVALID_CREDENTIALS: 'AUTH_001',
  /** 401. Too many failed attempts; the account is temporarily locked. */
  ACCOUNT_LOCKED: 'AUTH_003',
  /** 404. No school with that code. */
  UNKNOWN_SCHOOL: 'AUTH_005',
  /** 400 from the password endpoint. The `currentPassword` sent was not the account's. */
  CURRENT_PASSWORD_WRONG: 'AUTH_006',
  /** 400 from the password endpoint. The new password does not meet the policy. */
  WEAK_PASSWORD: 'AUTH_007',
} as const;

/**
 * HTTP access to the identity module.
 *
 * Every call sets `withCredentials` because the session is a cookie the server sets on login: the
 * browser will not attach it to a cross-origin XHR otherwise, and dev runs through a proxy today
 * but will not everywhere.
 */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<ApiResponse<LoginResponse>>(`${this.baseUrl}/login`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  logout(): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.baseUrl}/logout`, {}, { withCredentials: true })
      .pipe(unwrapVoid);
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.baseUrl}/password`, request, { withCredentials: true })
      .pipe(unwrapVoid);
  }
}
