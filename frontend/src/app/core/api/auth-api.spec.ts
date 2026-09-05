import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AUTH_ERROR, AuthApi } from './auth-api';
import { apiErrorCode } from './api-error';
import { LoginResponse } from './models';

const LOGIN_PAYLOAD: LoginResponse = {
  userId: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
  displayName: 'Priya Sharma',
  mustChangePassword: false,
  school: { code: 'GPS-S12', name: 'Greenfield Public School' },
};

describe('AuthApi', () => {
  let api: AuthApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    api = TestBed.inject(AuthApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('posts the credentials and hands back the payload without the envelope', () => {
    let received: LoginResponse | undefined;
    api
      .login({
        schoolCode: 'GPS-S12',
        username: 'priya.sharma',
        password: 'secret',
        rememberMe: false,
      })
      .subscribe({
        next: (response) => (received = response),
      });

    const request = httpMock.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      schoolCode: 'GPS-S12',
      username: 'priya.sharma',
      password: 'secret',
      rememberMe: false,
    });
    // The session is a cookie the server sets; without this the browser never sends it back.
    expect(request.request.withCredentials).toBe(true);

    request.flush({
      success: true,
      timestamp: '2026-09-05T10:00:00Z',
      traceId: 'test-trace',
      data: LOGIN_PAYLOAD,
    });

    expect(received).toEqual(LOGIN_PAYLOAD);
  });

  it('reports the forced password change the backend asks for', () => {
    let received: LoginResponse | undefined;
    api
      .login({
        schoolCode: 'GPS-S12',
        username: 'new.teacher',
        password: 'temp',
        rememberMe: false,
      })
      .subscribe({
        next: (response) => (received = response),
      });

    httpMock.expectOne('/api/auth/login').flush({
      success: true,
      timestamp: '2026-09-05T10:00:00Z',
      data: { ...LOGIN_PAYLOAD, mustChangePassword: true },
    });

    expect(received?.mustChangePassword).toBe(true);
  });

  it('surfaces AUTH_001 as a code callers can branch on', () => {
    let failure: unknown;
    api
      .login({
        schoolCode: 'GPS-S12',
        username: 'priya.sharma',
        password: 'wrong',
        rememberMe: false,
      })
      .subscribe({
        error: (error: unknown) => (failure = error),
      });

    httpMock.expectOne('/api/auth/login').flush(
      {
        success: false,
        timestamp: '2026-09-05T10:00:00Z',
        error: { code: AUTH_ERROR.INVALID_CREDENTIALS, message: 'Invalid username or password.' },
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(failure).toBeInstanceOf(HttpErrorResponse);
    expect(apiErrorCode(failure)).toBe('AUTH_001');
  });

  it('surfaces AUTH_003 for a locked account', () => {
    let failure: unknown;
    api
      .login({
        schoolCode: 'GPS-S12',
        username: 'priya.sharma',
        password: 'wrong',
        rememberMe: false,
      })
      .subscribe({
        error: (error: unknown) => (failure = error),
      });

    httpMock.expectOne('/api/auth/login').flush(
      {
        success: false,
        timestamp: '2026-09-05T10:00:00Z',
        error: { code: AUTH_ERROR.ACCOUNT_LOCKED, message: 'Account locked.' },
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(apiErrorCode(failure)).toBe('AUTH_003');
  });

  it('surfaces AUTH_005 for an unknown school code', () => {
    let failure: unknown;
    api
      .login({
        schoolCode: 'NOPE',
        username: 'priya.sharma',
        password: 'secret',
        rememberMe: false,
      })
      .subscribe({
        error: (error: unknown) => (failure = error),
      });

    httpMock.expectOne('/api/auth/login').flush(
      {
        success: false,
        timestamp: '2026-09-05T10:00:00Z',
        error: { code: AUTH_ERROR.UNKNOWN_SCHOOL, message: 'Unknown school code.' },
      },
      { status: 404, statusText: 'Not Found' },
    );

    expect(apiErrorCode(failure)).toBe('AUTH_005');
  });

  it('calls a failure with no envelope UNKNOWN rather than guessing', () => {
    let failure: unknown;
    api
      .login({
        schoolCode: 'GPS-S12',
        username: 'priya.sharma',
        password: 'secret',
        rememberMe: false,
      })
      .subscribe({
        error: (error: unknown) => (failure = error),
      });

    httpMock
      .expectOne('/api/auth/login')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(apiErrorCode(failure)).toBe('UNKNOWN');
  });

  it('completes a logout that carries no payload', () => {
    let completed = false;
    api.logout().subscribe({ complete: () => (completed = true) });

    const request = httpMock.expectOne('/api/auth/logout');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);

    // A void endpoint sends back an envelope with no `data` — that is success, not a malformed
    // response.
    request.flush({ success: true, timestamp: '2026-09-05T10:00:00Z' });

    expect(completed).toBe(true);
  });

  it('posts a password change with both passwords', () => {
    let completed = false;
    api
      .changePassword({ currentPassword: 'temp-one', newPassword: 'chalk-and-talk-9!' })
      .subscribe({ complete: () => (completed = true) });

    const request = httpMock.expectOne('/api/auth/password');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      currentPassword: 'temp-one',
      newPassword: 'chalk-and-talk-9!',
    });
    expect(request.request.withCredentials).toBe(true);

    request.flush({ success: true, timestamp: '2026-09-05T10:00:00Z' });

    expect(completed).toBe(true);
  });
});
