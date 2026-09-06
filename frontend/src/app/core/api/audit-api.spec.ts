import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { apiErrorCode } from './api-error';
import { AUDIT_MAX_PAGE_SIZE, AuditApi } from './audit-api';
import { AuditEvent, PageResponse } from './models';

const URL = '/api/audit';

/** An invented row. Never real school, staff or student data in a fixture. */
const EVENT: AuditEvent = {
  id: '018f3a10-0000-7000-8000-000000000001',
  occurredAt: '2026-09-05T09:14:00Z',
  actorId: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
  actorName: 'Priya Sharma',
  actorRoles: ['PRINCIPAL'],
  action: 'ENTITY_UPDATED',
  outcome: 'SUCCESS',
  entityType: 'SCHOOL_PROFILE',
  entityId: 'EVG-101',
  changedFields: ['phone', 'addressLine1'],
  ipAddress: '203.0.113.7',
  userAgent: 'Mozilla/5.0',
  traceId: 'test-trace',
};

const PAGE: PageResponse<AuditEvent> = {
  content: [EVENT],
  page: 0,
  size: 25,
  totalElements: 1,
  totalPages: 1,
};

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-05T10:00:00Z',
  traceId: 'test-trace',
  data,
});

describe('AuditApi', () => {
  let api: AuditApi;
  let httpMock: HttpTestingController;

  /**
   * The one in-flight search, matched on path because every call carries query parameters.
   *
   * Called once per request: `expectOne` takes the request off the open list, so asking twice for
   * the same one fails.
   */
  const search = () => httpMock.expectOne((request) => request.url === URL);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    api = TestBed.inject(AuditApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('hands back the page without the envelope, cookie and all', () => {
    let received: PageResponse<AuditEvent> | undefined;
    api.search().subscribe({ next: (page) => (received = page) });

    const request = search();
    expect(request.request.method).toBe('GET');
    // The session is a cookie the server set; without this the browser never sends it back.
    expect(request.request.withCredentials).toBe(true);

    request.flush(envelope(PAGE));

    expect(received).toEqual(PAGE);
  });

  it('asks for the first page, newest first, without inventing filters', () => {
    api.search().subscribe();

    const request = search();
    const { params } = request.request;
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('25');
    // Sent explicitly, not left to a default on the other side of the wire.
    expect(params.get('sort')).toBe('occurredAt,desc');
    // An absent filter is an absent parameter: `actorId=` would fail to bind to a UUID, and an
    // empty `action` would match nothing rather than everything.
    expect(params.has('actorId')).toBe(false);
    expect(params.has('action')).toBe(false);
    expect(params.has('from')).toBe(false);
    expect(params.has('to')).toBe(false);

    request.flush(envelope(PAGE));
  });

  it('passes every filter it was given', () => {
    api
      .search({
        page: 2,
        actorId: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
        action: 'LOGIN_FAILED',
        from: '2026-08-31T18:30:00.000Z',
        to: '2026-09-05T18:30:00.000Z',
      })
      .subscribe();

    const request = search();
    const { params } = request.request;
    expect(params.get('page')).toBe('2');
    expect(params.get('actorId')).toBe('2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55');
    expect(params.get('action')).toBe('LOGIN_FAILED');
    expect(params.get('from')).toBe('2026-08-31T18:30:00.000Z');
    expect(params.get('to')).toBe('2026-09-05T18:30:00.000Z');

    request.flush(envelope(PAGE));
  });

  /**
   * The backend clamps a larger size silently. Clamping here as well is what stops the screen's
   * arithmetic — "showing 101–200 of 137" — disagreeing with the rows it actually received.
   */
  it('never asks for more rows than the backend will serve', () => {
    api.search({ size: 500 }).subscribe();

    const request = search();
    expect(request.request.params.get('size')).toBe(String(AUDIT_MAX_PAGE_SIZE));

    request.flush(envelope(PAGE));
  });

  it('surfaces a refusal as a code the caller can branch on', () => {
    let failure: unknown;
    api.search().subscribe({ error: (error: unknown) => (failure = error) });

    search().flush(
      {
        success: false,
        timestamp: '2026-09-05T10:00:00Z',
        error: { code: 'PERM_001', message: 'You do not have permission to do that' },
      },
      { status: 403, statusText: 'Forbidden' },
    );

    expect(failure).toBeInstanceOf(HttpErrorResponse);
    expect(apiErrorCode(failure)).toBe('PERM_001');
  });
});
