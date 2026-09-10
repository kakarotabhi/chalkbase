import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { DocumentSummary } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { StudentDocuments } from './student-documents';

const STUDENT_A = '018f3a10-0000-7000-8000-000000000001';
const STUDENT_B = '018f3a10-0000-7000-8000-000000000002';
const DOCUMENTS = '/api/documents';

const documentFixture = (over: Partial<DocumentSummary> = {}): DocumentSummary => ({
  id: 'doc-1',
  studentId: STUDENT_A,
  documentType: 'BIRTH_CERTIFICATE',
  verificationStatus: 'UNVERIFIED',
  originalFilename: 'certificate.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  createdAt: '2026-09-01T10:00:00Z',
  ...over,
});

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-06T10:00:00Z',
  traceId: 'test-trace',
  data,
});

describe('StudentDocuments', () => {
  let fixture: ComponentFixture<StudentDocuments>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const listRequest = () =>
    httpMock.expectOne((request) => request.url === DOCUMENTS && request.method === 'GET');

  const button = (label: string) =>
    Array.from(element().querySelectorAll('button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement;

  /** Creates the panel for `studentId` and answers its list request. */
  const arrive = (studentId: string = STUDENT_A, documents: readonly DocumentSummary[] = []) => {
    fixture = TestBed.createComponent(StudentDocuments);
    fixture.componentRef.setInput('studentId', studentId);
    fixture.detectChanges();
    listRequest().flush(envelope(documents));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudentDocuments],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    signInWith(Permissions.DOCUMENT_READ, Permissions.DOCUMENT_MANAGE);
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  it('shows "No documents yet" once the empty list answers', () => {
    arrive();

    expect(text()).toContain('No documents yet');
    expect(text()).toContain('Add the first one above');
  });

  /**
   * The bug this guards: `startAdd()` used to flip `adding` to true, which mounted `#fileInput`
   * (it lives behind `@if (adding())` in the template), which changed what the constructor
   * effect's own `closeAdd()` read, which re-ran the effect and immediately flipped `adding` back
   * to false — so the form never stayed open. See the constructor's comment in
   * `student-documents.ts`.
   */
  it('opens the upload form on "Add a document" and keeps it open', () => {
    arrive();

    expect(element().querySelector('#document-type')).toBeNull();

    button('Add a document').click();
    fixture.detectChanges();

    expect(element().querySelector('#document-type')).not.toBeNull();
    expect(element().querySelector('#document-file')).not.toBeNull();
    // Nothing re-triggers a second list request just because the form opened.
    httpMock.expectNone((request) => request.url === DOCUMENTS && request.method === 'GET');
  });

  it('closes the upload form on Cancel', () => {
    arrive();

    button('Add a document').click();
    fixture.detectChanges();
    expect(element().querySelector('#document-type')).not.toBeNull();

    button('Cancel').click();
    fixture.detectChanges();

    expect(element().querySelector('#document-type')).toBeNull();
    expect(button('Add a document')).not.toBeUndefined();
  });

  /**
   * Changing the route's student id is the effect's one real dependency: it resets whatever was
   * open for the old student and reloads from the new id. Opening the form for student A must not
   * survive a switch to student B, and must not leave a stale row from A on screen either.
   */
  it('resets and reloads when the student id changes', () => {
    arrive(STUDENT_A, [documentFixture({ id: 'doc-a', originalFilename: 'a-certificate.pdf' })]);
    expect(text()).toContain('a-certificate.pdf');

    button('Add a document').click();
    fixture.detectChanges();
    expect(element().querySelector('#document-type')).not.toBeNull();

    fixture.componentRef.setInput('studentId', STUDENT_B);
    fixture.detectChanges();

    // The reset closes the form immediately, before the new list has even answered.
    expect(element().querySelector('#document-type')).toBeNull();
    expect(text()).not.toContain('a-certificate.pdf');

    listRequest().flush(
      envelope([
        documentFixture({ id: 'doc-b', studentId: STUDENT_B, originalFilename: 'b-photo.pdf' }),
      ]),
    );
    fixture.detectChanges();

    expect(text()).toContain('b-photo.pdf');
  });

  it('offers no "Add a document" button to somebody who may only read documents', () => {
    signInWith(Permissions.DOCUMENT_READ);
    arrive();

    expect(button('Add a document')).toBeUndefined();
  });
});
