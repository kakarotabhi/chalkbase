import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import {
  AcademicSession,
  SchoolClass,
  StudentDetail as StudentRecord,
} from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { StudentDetail } from './student-detail';

const STUDENT = '018f3a10-0000-7000-8000-000000000001';
const RECORD = `/api/students/${STUDENT}`;
const SESSIONS = '/api/academics/sessions';
const CLASSES = '/api/academics/classes';

const sessions: AcademicSession[] = [
  { id: 'y1', name: '2026–27', startsOn: '2026-04-01', endsOn: '2027-03-31', current: true },
  { id: 'y0', name: '2025–26', startsOn: '2025-04-01', endsOn: '2026-03-31', current: false },
];

const ladder: SchoolClass[] = [
  {
    id: 'c1',
    name: 'Class 5',
    sequence: 5,
    active: true,
    sections: [{ id: 'sec-a', name: 'A', active: true }],
  },
];

/**
 * An invented child at an invented school.
 *
 * `email` and `occupation` are missing from the guardian rather than set to null, and so is the
 * roll number: the backend serialises with `default-property-inclusion: non_null`, so a field with
 * no value is left out of the JSON entirely. A fixture that sent null would be exercising a shape
 * the server never produces.
 */
const record = (over: Partial<StudentRecord> = {}): StudentRecord => ({
  id: STUDENT,
  admissionNumber: 'EVG/2026/0148',
  fullName: 'Test Student One',
  gender: 'FEMALE',
  status: 'ACTIVE',
  dateOfBirth: '2015-04-02',
  admittedOn: '2026-04-01',
  currentEnrolment: {
    sessionName: '2026–27',
    className: 'Class 5',
    sectionName: 'A',
    rollNumber: '12',
  },
  guardians: [
    {
      linkId: 'link-1',
      guardianId: 'g-1',
      fullName: 'Test Parent One',
      relation: 'FATHER',
      phone: '9000000001',
      primary: true,
    },
    {
      linkId: 'link-2',
      guardianId: 'g-2',
      fullName: 'Test Parent Two',
      relation: 'MOTHER',
      phone: '9000000002',
      primary: false,
    },
  ],
  enrolments: [
    {
      id: 'e-1',
      sessionId: 'y1',
      sessionName: '2026–27',
      classId: 'c1',
      className: 'Class 5',
      sectionId: 'sec-a',
      sectionName: 'A',
      rollNumber: '12',
      active: true,
      enrolledOn: '2026-04-05',
    },
  ],
  ...over,
});

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-06T10:00:00Z',
  traceId: 'test-trace',
  data,
});

const refusal = (code: string) => ({
  success: false,
  timestamp: '2026-09-06T10:00:00Z',
  error: { code, message: 'Refused.' },
});

describe('StudentDetail', () => {
  let fixture: ComponentFixture<StudentDetail>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const load = () => httpMock.expectOne({ url: RECORD, method: 'GET' });

  const button = (label: string) =>
    Array.from(element().querySelectorAll('button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement;

  const type = (id: string, value: string) => {
    const input = element().querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const choose = (id: string, value: string) => {
    const select = element().querySelector(`#${id}`) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

  /** Creates the screen, answers the record, then answers what the enrolments panel asks for. */
  const arrive = (student: StudentRecord = record()) => {
    fixture = TestBed.createComponent(StudentDetail);
    fixture.componentRef.setInput('id', STUDENT);
    fixture.detectChanges();
    load().flush(envelope(student));
    fixture.detectChanges();
    httpMock.expectOne({ url: SESSIONS, method: 'GET' }).flush(envelope(sessions));
    httpMock.expectOne({ url: CLASSES, method: 'GET' }).flush(envelope(ladder));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudentDetail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    // The default for every test below: somebody who may do the things this screen offers.
    // The tests that care sign a different user in instead, and none of them mocks the
    // permission check — the real `SessionStore` is what the templates read.
    signInWith(Permissions.STUDENT_READ, Permissions.STUDENT_MANAGE, Permissions.GUARDIAN_MANAGE);
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  // ── The record ───────────────────────────────────────────────────────────────────────────

  it('shows the record, the guardians and the enrolment history from one request', () => {
    arrive();

    expect(text()).toContain('Test Student One');
    expect(text()).toContain('EVG/2026/0148');
    expect(text()).toContain('2 Apr 2015');
    expect(text()).toContain('Class 5 · A');
    expect(text()).toContain('Test Parent One');
    expect(text()).toContain('Main contact');
    expect(text()).toContain('2026–27');
  });

  /** Absent, not null — see the fixture. An unrecorded admission date is a real state. */
  it('says an admission date is not recorded when the server left the field out', () => {
    const { admittedOn: _omitted, ...withoutAdmittedOn } = record();
    arrive(withoutAdmittedOn as StudentRecord);

    expect(text()).toContain('Not recorded');
  });

  /** ADR-0020 §6: a student is withdrawn, never removed. */
  it('offers no way to delete the student', () => {
    arrive();

    const labels = Array.from(element().querySelectorAll('button')).map((candidate) =>
      (candidate.textContent ?? '').trim().toLowerCase(),
    );
    expect(labels.some((label) => label.includes('delete'))).toBe(false);
    // The one "remove" on the screen is about a link, and it says so.
    expect(labels.filter((label) => label.includes('remove'))).toEqual([
      'remove from this student',
      'remove from this student',
    ]);
  });

  it('explains a 404 as an address that does not resolve, never as a deleted record', () => {
    fixture = TestBed.createComponent(StudentDetail);
    fixture.componentRef.setInput('id', STUDENT);
    fixture.detectChanges();
    load().flush(refusal('NF_001'), { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(text()).toContain('There is no such student at this school');
    expect(text().toLowerCase()).not.toContain('deleted');
  });

  /** No client-side permission guard, deliberately (ADR-0008). */
  it('explains a 403 rather than redirecting', () => {
    fixture = TestBed.createComponent(StudentDetail);
    fixture.componentRef.setInput('id', STUDENT);
    fixture.detectChanges();
    load().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view this student');
  });

  it('saves an edit and renders the record the server answered with', () => {
    arrive();

    button('Edit details').click();
    fixture.detectChanges();

    type('student-full-name', 'Test Student One Corrected');
    button('Save student').click();
    fixture.detectChanges();

    const request = httpMock.expectOne({ url: RECORD, method: 'PUT' });
    expect(request.request.body.fullName).toBe('Test Student One Corrected');
    request.flush(envelope(record({ fullName: 'Test Student One Corrected' })));
    fixture.detectChanges();

    expect(text()).toContain('Test Student One Corrected');
    expect(text()).toContain('The record was saved');
  });

  // ── Guardians ────────────────────────────────────────────────────────────────────────────

  /**
   * Two rows change on one request — the new main contact is set and the old one is cleared,
   * server-side — so the screen re-reads the record rather than patching the row it asked about.
   */
  it('makes a guardian the main contact and re-reads the whole record', () => {
    arrive();

    button('Make main contact').click();
    fixture.detectChanges();

    const request = httpMock.expectOne({
      url: `${RECORD}/guardians/link-2`,
      method: 'PUT',
    });
    // The relation goes back unchanged: this endpoint replaces the link, so leaving it out would
    // blank it as a side effect of ticking a box.
    expect(request.request.body).toEqual({ relation: 'MOTHER', primary: true });
    request.flush(envelope(null));
    fixture.detectChanges();

    load().flush(
      envelope(
        record({
          guardians: [
            {
              linkId: 'link-1',
              guardianId: 'g-1',
              fullName: 'Test Parent One',
              relation: 'FATHER',
              phone: '9000000001',
              primary: false,
            },
            {
              linkId: 'link-2',
              guardianId: 'g-2',
              fullName: 'Test Parent Two',
              relation: 'MOTHER',
              phone: '9000000002',
              primary: true,
            },
          ],
        }),
      ),
    );
    fixture.detectChanges();

    expect(text()).toContain('Test Parent Two is now the main contact');
  });

  /**
   * The one delete on these screens, and the confirmation has to say what it actually does: the
   * guardian record survives, because their other children still point at it (ADR-0020 §5).
   */
  it('asks before detaching a guardian, and says the person is not deleted', () => {
    arrive();

    button('Remove from this student').click();
    fixture.detectChanges();

    expect(text()).toContain('Remove Test Parent One from this student?');
    expect(text()).toContain('stays on the guardian list');
    expect(text()).toContain('any other children of theirs');

    (element().querySelector('.dialog__confirm button') as HTMLButtonElement).click();
    fixture.detectChanges();

    httpMock
      .expectOne({ url: `${RECORD}/guardians/link-1`, method: 'DELETE' })
      .flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    load().flush(envelope(record({ guardians: [] })));
    fixture.detectChanges();

    expect(text()).toContain('Their guardian record is unchanged');
  });

  // ── Enrolments ───────────────────────────────────────────────────────────────────────────

  /**
   * The class picker only narrows the section list; a section belongs to exactly one class, so the
   * request carries the section and nothing about the class.
   */
  it('adds an enrolment from a session, a class and a section, with no roll number', () => {
    arrive();

    button('Add an enrolment').click();
    fixture.detectChanges();

    // The current session is already chosen: it is the year everything academic is recorded
    // against, and therefore the one an enrolment is nearly always for.
    expect((element().querySelector('#enrolment-session') as HTMLSelectElement).value).toBe('y1');

    choose('enrolment-class', 'c1');
    choose('enrolment-section', 'sec-a');
    button('Add enrolment').click();
    fixture.detectChanges();

    const request = httpMock.expectOne({ url: `${RECORD}/enrolments`, method: 'POST' });
    expect(request.request.body).toEqual({
      academicSessionId: 'y1',
      sectionId: 'sec-a',
      // Omitted, not "": the column is nullable because a roll number is assigned later, and an
      // empty string is a value rather than the absence of one. `undefined` here is the key being
      // present on the body object; `JSON.stringify` drops it, so nothing reaches the wire and the
      // record component arrives null — which is what the contract describes.
      rollNumber: undefined,
    });
    request.flush(envelope(null));
    fixture.detectChanges();

    load().flush(envelope(record()));
    fixture.detectChanges();

    expect(text()).toContain('The enrolment was added');
  });

  /**
   * Losing the academic structure costs the ability to add an enrolment, not the history — which
   * is also the ordinary answer for a role that may read students but not the academic model.
   */
  it('still shows the enrolment history when the classes cannot be loaded', () => {
    fixture = TestBed.createComponent(StudentDetail);
    fixture.componentRef.setInput('id', STUDENT);
    fixture.detectChanges();
    load().flush(envelope(record()));
    fixture.detectChanges();
    // The two are one `forkJoin`, so the sibling is cancelled the moment this one fails — which
    // is the point of asking for them together: the form is unusable without both.
    httpMock.expectOne({ url: CLASSES, method: 'GET' });
    httpMock
      .expectOne({ url: SESSIONS, method: 'GET' })
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You cannot change enrolments');
    expect(text()).toContain('Class 5 · A');
    expect(button('Add an enrolment')).toBeUndefined();
  });

  // ── What the record's actions are gated on ───────────────────────────────────────────────

  describe('write actions', () => {
    const control = (id: string) => element().querySelector(`#${id}`);

    it('offers editing, enrolling and attaching a guardian to somebody who may do all three', () => {
      arrive();

      expect(control('student-edit')).not.toBeNull();
      expect(control('enrolment-add')).not.toBeNull();
      expect(control('enrolment-edit-e-1')).not.toBeNull();
      expect(control('guardian-attach-open')).not.toBeNull();
      expect(control('guardian-edit-link-1')).not.toBeNull();
      expect(control('guardian-remove-link-1')).not.toBeNull();
    });

    it('offers none of them to a classteacher who may only read the record', () => {
      signInWith(Permissions.STUDENT_READ, Permissions.GUARDIAN_READ);
      arrive();

      expect(control('student-edit')).toBeNull();
      expect(control('enrolment-add')).toBeNull();
      expect(control('enrolment-edit-e-1')).toBeNull();
      expect(control('guardian-attach-open')).toBeNull();
      expect(control('guardian-edit-link-1')).toBeNull();
      expect(control('guardian-remove-link-1')).toBeNull();
      // What the read entitles them to is still there: the child, their guardians, their history.
      expect(text()).toContain('Test Student One');
    });

    /**
     * The two resources are separate on the backend precisely so a school can hand somebody the
     * class roster without handing them the parents' phone numbers. The screen has to draw the
     * same line, or the guardian permission is decorative.
     */
    it('gates the guardian actions on the guardian permission, not the student one', () => {
      signInWith(Permissions.STUDENT_READ, Permissions.STUDENT_MANAGE);
      arrive();

      expect(control('student-edit')).not.toBeNull();
      expect(control('guardian-attach-open')).toBeNull();
      expect(control('guardian-edit-link-1')).toBeNull();
    });

    it('reads the live session, not a snapshot taken when the screen was built', () => {
      signInWith(Permissions.STUDENT_READ);
      arrive();
      expect(control('student-edit')).toBeNull();

      signInWith(Permissions.STUDENT_READ, Permissions.STUDENT_MANAGE);
      fixture.detectChanges();

      expect(control('student-edit')).not.toBeNull();
    });
  });
});
