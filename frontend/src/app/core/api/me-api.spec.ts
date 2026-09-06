import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { apiErrorCode } from './api-error';
import { AUTH_ERROR } from './auth-api';
import { MeApi } from './me-api';
import { MeResponse } from './models';

const ME: MeResponse = {
  user: {
    id: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
    displayName: 'Priya Sharma',
    mustChangePassword: false,
  },
  school: { code: 'GPS-S12', name: 'Greenfield Public School' },
  permissionsVersion: '7',
  permissions: ['school:school:read', 'fees:receipt:create'],
  navigation: [
    { id: 'schools', labelKey: 'nav.schools', icon: 'school', order: 10, children: [] },
    {
      id: 'fees',
      labelKey: 'nav.fees',
      icon: 'receipt',
      order: 20,
      children: [{ id: 'fees.collect', labelKey: 'nav.fees.collect', order: 10, children: [] }],
    },
  ],
};

describe('MeApi', () => {
  let api: MeApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    api = TestBed.inject(MeApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('hands back the payload without the envelope, cookie and all', () => {
    let received: MeResponse | undefined;
    api.get().subscribe({ next: (response) => (received = response) });

    const request = httpMock.expectOne('/api/me');
    expect(request.request.method).toBe('GET');
    // The session is a cookie the server set; without this the browser never sends it back.
    expect(request.request.withCredentials).toBe(true);

    request.flush({
      success: true,
      timestamp: '2026-09-05T10:00:00Z',
      traceId: 'test-trace',
      data: ME,
    });

    expect(received).toEqual(ME);
    // The navigation tree arrives intact, nesting included — resolving it is NavigationStore's job.
    expect(received?.navigation[1].children[0].id).toBe('fees.collect');
  });

  it('surfaces AUTH_002 as a code the caller can branch on', () => {
    let failure: unknown;
    api.get().subscribe({ error: (error: unknown) => (failure = error) });

    httpMock.expectOne('/api/me').flush(
      {
        success: false,
        timestamp: '2026-09-05T10:00:00Z',
        error: { code: AUTH_ERROR.NO_SESSION, message: 'Sign in to continue.' },
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(failure).toBeInstanceOf(HttpErrorResponse);
    expect(apiErrorCode(failure)).toBe('AUTH_002');
  });
});
