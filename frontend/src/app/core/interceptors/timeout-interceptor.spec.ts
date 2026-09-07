import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { REQUEST_TIMEOUT_MS, timeoutInterceptor } from './timeout-interceptor';

describe('timeoutInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([timeoutInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  it('fails a hung request once the limit passes, instead of waiting forever', () => {
    let settled: 'value' | 'error' | null = null;
    http.get('/api/students').subscribe({
      next: () => (settled = 'value'),
      error: () => (settled = 'error'),
    });

    // Never flushed — this is the hang the interceptor exists for.
    httpMock.expectOne('/api/students');

    vi.advanceTimersByTime(REQUEST_TIMEOUT_MS - 1);
    expect(settled).toBeNull();

    vi.advanceTimersByTime(2);
    expect(settled).toBe('error');
  });

  it('leaves a request that answers in time alone', () => {
    let settled: 'value' | 'error' | null = null;
    http.get('/api/students').subscribe({
      next: () => (settled = 'value'),
      error: () => (settled = 'error'),
    });

    vi.advanceTimersByTime(REQUEST_TIMEOUT_MS - 1);
    httpMock.expectOne('/api/students').flush({ success: true, data: [] });

    expect(settled).toBe('value');
  });

  it('never times out GET /api/me, no matter how long it waits', () => {
    let settled: 'value' | 'error' | null = null;
    http.get('/api/me').subscribe({
      next: () => (settled = 'value'),
      error: () => (settled = 'error'),
    });

    const req = httpMock.expectOne('/api/me');
    // Comfortably past both the request timeout and the worst measured Render cold start — see the
    // interceptor's own doc comment for why this endpoint is the one exception.
    vi.advanceTimersByTime(REQUEST_TIMEOUT_MS * 5);
    expect(settled).toBeNull();

    req.flush({ success: true, data: {} });
    expect(settled).toBe('value');
  });
});
