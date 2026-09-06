import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { GuardiansApi } from './guardians-api';

const GUARDIANS = '/api/guardians';

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-06T10:00:00Z',
  traceId: 'test-trace',
  data,
});

describe('GuardiansApi', () => {
  let api: GuardiansApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    api = TestBed.inject(GuardiansApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('sends the session cookie with every call', () => {
    api.search().subscribe();
    api.create({ fullName: 'A Parent', phone: '', email: '', occupation: '' }).subscribe();
    api.update('g1', { fullName: 'A Parent', phone: '', email: '', occupation: '' }).subscribe();

    const requests = httpMock.match(() => true);
    expect(requests).toHaveLength(3);
    for (const request of requests) {
      expect(request.request.withCredentials).toBe(true);
      request.flush(envelope(null));
    }
  });

  it('leaves an empty search out of the query rather than sending q=', () => {
    api.search({ q: '  ' }).subscribe();

    const request = httpMock.expectOne((candidate) => candidate.url === GUARDIANS);
    expect(request.request.params.has('q')).toBe(false);
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('25');
    request.flush(envelope({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }));
  });

  /**
   * The id in this answer is load-bearing: creating a guardian from inside "attach a guardian to
   * this child" is create-then-link, and the link needs the id the create returned.
   */
  it('answers a create with the guardian that was created', () => {
    const guardian = {
      id: 'g-9',
      fullName: 'A Parent',
      phone: '9000000000',
      email: null,
      occupation: null,
      linkedStudentCount: 0,
    };
    let received: unknown;
    api
      .create({ fullName: 'A Parent', phone: '9000000000', email: '', occupation: '' })
      .subscribe((value) => (received = value));

    httpMock.expectOne({ url: GUARDIANS, method: 'POST' }).flush(envelope(guardian));

    expect(received).toEqual(guardian);
  });

  /**
   * Not paged, and that is the shape rather than an omission: a guardian has a handful of children,
   * so this answers a plain list. A 403 is an ordinary answer here — the endpoint is guarded by
   * `student:student:read` while the directory is guarded by `student:guardian:read`.
   */
  it("reads a guardian's students as a plain list", () => {
    let received: unknown;
    api.students('g-1').subscribe((value) => (received = value));

    const request = httpMock.expectOne({ url: `${GUARDIANS}/g-1/students`, method: 'GET' });
    expect(request.request.withCredentials).toBe(true);
    request.flush(
      envelope([
        {
          studentId: 's-1',
          fullName: 'A Child',
          admissionNumber: '2026/0001',
          relation: 'FATHER',
          primary: true,
        },
      ]),
    );

    expect(received).toHaveLength(1);
  });

  /**
   * ADR-0020 §6: nothing here is deleted. Detaching a guardian from one child is a different
   * operation on a different resource, and it lives on `StudentsApi`.
   */
  it('has no way to delete a guardian', () => {
    expect(Object.getOwnPropertyNames(GuardiansApi.prototype)).not.toContain('delete');
  });
});
