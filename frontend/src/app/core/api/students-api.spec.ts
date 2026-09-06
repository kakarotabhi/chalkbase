import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { StudentsApi } from './students-api';

const STUDENTS = '/api/students';

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-06T10:00:00Z',
  traceId: 'test-trace',
  data,
});

describe('StudentsApi', () => {
  let api: StudentsApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    api = TestBed.inject(StudentsApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  /**
   * The session is a cookie the server set, and a browser will not attach it to a cross-origin
   * request without this. Nothing on a screen can see whether it did — the failure is a 401 in
   * production and nothing in a component test, which is exactly why it is asserted here.
   */
  it('sends the session cookie with every call', () => {
    api.search().subscribe();
    api.get('s1').subscribe();
    api
      .create({
        admissionNumber: 'A-1',
        fullName: 'Test Student',
        dateOfBirth: '2015-04-02',
        gender: 'FEMALE',
        status: 'ACTIVE',
        admittedOn: '2026-04-01',
      })
      .subscribe();
    api
      .addEnrolment('s1', { academicSessionId: 'y1', sectionId: 'sec1', rollNumber: null })
      .subscribe();
    api.linkGuardian('s1', { guardianId: 'g1', relation: 'FATHER', primary: true }).subscribe();
    api.detachGuardian('s1', 'l1').subscribe();

    const requests = httpMock.match(() => true);
    expect(requests).toHaveLength(6);
    for (const request of requests) {
      expect(request.request.withCredentials).toBe(true);
      request.flush(envelope(null));
    }
  });

  it('asks for the first page by name, and says so rather than relying on a default', () => {
    api.search().subscribe();

    const request = httpMock.expectOne((candidate) => candidate.url === STUDENTS);
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.params.get('sort')).toBe('fullName,asc');
    request.flush(envelope({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }));
  });

  /**
   * An empty filter has to be absent, not empty: the backend binds `sectionId=` to a UUID by
   * failing the request, and `status=` would match nothing at all.
   */
  it('leaves an empty filter out of the query entirely', () => {
    api.search({ q: '   ', status: null, sectionId: '' }).subscribe();

    const request = httpMock.expectOne((candidate) => candidate.url === STUDENTS);
    expect(request.request.params.has('q')).toBe(false);
    expect(request.request.params.has('status')).toBe(false);
    expect(request.request.params.has('sectionId')).toBe(false);
    request.flush(envelope({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }));
  });

  it('sends the filters it was given, trimmed', () => {
    api.search({ page: 2, q: '  Aarav  ', status: 'WITHDRAWN', sectionId: 'sec-9' }).subscribe();

    const request = httpMock.expectOne((candidate) => candidate.url === STUDENTS);
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('q')).toBe('Aarav');
    expect(request.request.params.get('status')).toBe('WITHDRAWN');
    expect(request.request.params.get('sectionId')).toBe('sec-9');
    request.flush(envelope({ content: [], page: 2, size: 25, totalElements: 0, totalPages: 0 }));
  });

  /** The backend clamps a larger page rather than refusing it, so the arithmetic here must agree. */
  it('clamps the page size to what the backend will actually serve', () => {
    api.search({ size: 5000 }).subscribe();

    const request = httpMock.expectOne((candidate) => candidate.url === STUDENTS);
    expect(request.request.params.get('size')).toBe('100');
    request.flush(envelope({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }));
  });

  it('unwraps the envelope so callers never see the transport shape', () => {
    const student = {
      id: 's1',
      admissionNumber: 'A-1',
      fullName: 'Test Student',
      gender: 'FEMALE',
      status: 'ACTIVE',
      currentEnrolment: null,
      dateOfBirth: '2015-04-02',
      admittedOn: '2026-04-01',
      guardians: [],
      enrolments: [],
    };
    let received: unknown;
    api.get('s1').subscribe((value) => (received = value));

    httpMock.expectOne({ url: `${STUDENTS}/s1`, method: 'GET' }).flush(envelope(student));

    expect(received).toEqual(student);
  });

  /**
   * A 204 carries no envelope at all. Unwrapping one would read `success` off null and throw,
   * turning a successful detach into an error on screen.
   */
  it('completes a detach that answers 204 with no body', () => {
    let completed = false;
    api.detachGuardian('s1', 'l1').subscribe({ complete: () => (completed = true) });

    httpMock
      .expectOne({ url: `${STUDENTS}/s1/guardians/l1`, method: 'DELETE' })
      .flush(null, { status: 204, statusText: 'No Content' });

    expect(completed).toBe(true);
  });

  it('addresses a guardian link by its link id, not by the guardian', () => {
    api.updateGuardianLink('s1', 'link-7', { relation: 'MOTHER', primary: true }).subscribe();

    const request = httpMock.expectOne({
      url: `${STUDENTS}/s1/guardians/link-7`,
      method: 'PUT',
    });
    expect(request.request.body).toEqual({ relation: 'MOTHER', primary: true });
    request.flush(envelope(null));
  });

  /**
   * ADR-0020 §6 decided nothing here is deleted. A student is `WITHDRAWN`, never removed, and a
   * method here would be the first step towards a `DELETE` endpoint appearing.
   */
  it('has no way to delete a student', () => {
    const methods = Object.getOwnPropertyNames(StudentsApi.prototype);
    expect(methods).not.toContain('delete');
    expect(methods).not.toContain('deleteStudent');
    // The one delete is a link, and it is named so that nobody reads it as deleting a person.
    expect(methods).toContain('detachGuardian');
  });
});
