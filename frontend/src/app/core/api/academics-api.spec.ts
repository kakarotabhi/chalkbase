import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AcademicsApi } from './academics-api';

const SESSIONS = '/api/academics/sessions';
const CLASSES = '/api/academics/classes';

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-06T10:00:00Z',
  traceId: 'test-trace',
  data,
});

describe('AcademicsApi', () => {
  let api: AcademicsApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    api = TestBed.inject(AcademicsApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  /**
   * The session is a cookie the server set, and a browser will not attach it to a cross-origin
   * request without this. Every call behind a permission carries it, and nothing on a screen can
   * see whether it did — the failure is a 401 in production and nothing in a component test, which
   * is exactly why it is asserted here.
   */
  it('sends the session cookie with every call', () => {
    api.sessions().subscribe();
    api.classes().subscribe();
    api.createClass({ name: 'Class 1' }).subscribe();
    api.reorderClasses(['a', 'b']).subscribe();
    api.makeSessionCurrent('s1').subscribe();
    api.updateSection('sec', { name: 'A', active: true }).subscribe();

    const requests = httpMock.match(() => true);
    expect(requests).toHaveLength(6);
    for (const request of requests) {
      expect(request.request.withCredentials).toBe(true);
      request.flush(envelope(null));
    }
  });

  it('unwraps the envelope so callers never see the transport shape', () => {
    const session = {
      id: 's1',
      name: '2026–27',
      startsOn: '2026-04-01',
      endsOn: '2027-03-31',
      current: true,
    };
    let received: unknown;
    api.sessions().subscribe((sessions) => (received = sessions));

    httpMock.expectOne({ url: SESSIONS, method: 'GET' }).flush(envelope([session]));

    expect(received).toEqual([session]);
  });

  /**
   * `/classes/order` is about the ladder, not about one class, and it takes every id. A copy is
   * sent rather than the caller's array so a screen holding its optimistic order cannot have that
   * array mutated out from under it.
   */
  it('reorders through the ladder-wide endpoint, with the ids it was given', () => {
    const ids = ['a', 'b', 'c'];
    api.reorderClasses(ids).subscribe();

    const request = httpMock.expectOne({ url: `${CLASSES}/order`, method: 'PUT' });
    expect(request.request.body).toEqual({ classIds: ['a', 'b', 'c'] });
    expect(request.request.body.classIds).not.toBe(ids);
    request.flush(envelope([]));
  });

  it('makes a session current with a POST to the session it names', () => {
    api.makeSessionCurrent('s2').subscribe();

    const request = httpMock.expectOne({ url: `${SESSIONS}/s2/current`, method: 'POST' });
    // No body: the id in the URL is the whole request, and there is nothing here that could ask
    // the server to clear the previous one as a second step.
    expect(request.request.body).toBeNull();
    // And the answer is every session, because two of them changed.
    request.flush(envelope([]));
  });

  /**
   * ADR-0019 decided rows are deactivated rather than removed. There is no delete endpoint, and a
   * method here would be the first step towards one appearing.
   */
  it('has no way to delete a class or a section', () => {
    expect(Object.getOwnPropertyNames(AcademicsApi.prototype)).not.toContain('deleteClass');
    expect(Object.getOwnPropertyNames(AcademicsApi.prototype)).not.toContain('deleteSection');
  });
});
