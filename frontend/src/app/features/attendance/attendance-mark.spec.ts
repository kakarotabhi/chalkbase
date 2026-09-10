import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SchoolClass, SectionAttendanceView } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { AttendanceMark } from './attendance-mark';

const CLASSES_URL = '/api/academics/classes';

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-07T10:00:00Z',
  traceId: 'test-trace',
  data,
});

const refusal = (code: string) => ({
  success: false,
  timestamp: '2026-09-07T10:00:00Z',
  error: { code, message: 'Refused.' },
});

const oneClass = (): SchoolClass[] => [
  {
    id: 'class-5',
    name: 'Class 5',
    sequence: 5,
    active: true,
    sections: [{ id: 'section-5a', name: 'A', active: true }],
  },
];

const view = (over: Partial<SectionAttendanceView> = {}): SectionAttendanceView => ({
  sectionId: 'section-5a',
  sectionName: 'A',
  className: 'Class 5',
  academicSessionId: 'session-1',
  attendanceDate: '2026-09-07',
  locked: false,
  entries: [
    {
      studentId: 'student-1',
      admissionNumber: 'ADM-001',
      fullName: 'Aarav Sharma',
      rollNumber: '1',
      editable: true,
      approvedLeave: false,
    },
  ],
  ...over,
});

describe('AttendanceMark', () => {
  let fixture: ComponentFixture<AttendanceMark>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AttendanceMark],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('says so when the caller picks a section they cannot see attendance for', () => {
    signInWith();
    fixture = TestBed.createComponent(AttendanceMark);
    fixture.detectChanges();
    httpMock.expectOne(CLASSES_URL).flush(envelope(oneClass()));
    fixture.detectChanges();

    const select = element().querySelector<HTMLSelectElement>('#attendance-section');
    select!.value = 'section-5a';
    select!.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    httpMock
      .expectOne((candidate) => candidate.url === '/api/attendance/sections/section-5a')
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view attendance');
  });

  it('loads the roster once a section is chosen, and shows each student', () => {
    signInWith(Permissions.ATTENDANCE_READ, Permissions.ATTENDANCE_MANAGE);
    fixture = TestBed.createComponent(AttendanceMark);
    fixture.detectChanges();
    httpMock.expectOne(CLASSES_URL).flush(envelope(oneClass()));
    fixture.detectChanges();

    const select = element().querySelector<HTMLSelectElement>('#attendance-section');
    expect(select).toBeTruthy();
    select!.value = 'section-5a';
    select!.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const request = httpMock.expectOne(
      (candidate) => candidate.url === '/api/attendance/sections/section-5a',
    );
    request.flush(envelope(view()));
    fixture.detectChanges();

    expect(text()).toContain('Aarav Sharma');
    expect(text()).toContain('Mark all present');
  });

  it('says plainly that nothing was marked when a locked date has no marks at all', () => {
    signInWith(Permissions.ATTENDANCE_READ, Permissions.ATTENDANCE_MANAGE);
    fixture = TestBed.createComponent(AttendanceMark);
    fixture.detectChanges();
    httpMock.expectOne(CLASSES_URL).flush(envelope(oneClass()));
    fixture.detectChanges();

    const select = element().querySelector<HTMLSelectElement>('#attendance-section');
    select!.value = 'section-5a';
    select!.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const req = httpMock.expectOne(
      (candidate) => candidate.url === '/api/attendance/sections/section-5a',
    );
    req.flush(envelope(view({ locked: true })));
    fixture.detectChanges();

    expect(text()).toContain('nobody was marked on it');
    expect(text()).not.toContain('Request a correction on the student who needs one');
    expect(element().querySelector('.link-button')).toBeNull();
  });

  it('points at the per-row correction control when a locked date does have a mark', () => {
    signInWith(Permissions.ATTENDANCE_READ, Permissions.ATTENDANCE_MANAGE);
    fixture = TestBed.createComponent(AttendanceMark);
    fixture.detectChanges();
    httpMock.expectOne(CLASSES_URL).flush(envelope(oneClass()));
    fixture.detectChanges();

    const select = element().querySelector<HTMLSelectElement>('#attendance-section');
    select!.value = 'section-5a';
    select!.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const req = httpMock.expectOne(
      (candidate) => candidate.url === '/api/attendance/sections/section-5a',
    );
    req.flush(
      envelope(
        view({
          locked: true,
          entries: [
            {
              studentId: 'student-1',
              admissionNumber: 'ADM-001',
              fullName: 'Aarav Sharma',
              rollNumber: '1',
              editable: false,
              approvedLeave: false,
              markId: 'mark-1',
              status: 'PRESENT',
            },
          ],
        }),
      ),
    );
    fixture.detectChanges();

    expect(text()).toContain('Request a correction on the student who needs one instead.');
    const requestButton = Array.from(element().querySelectorAll('.link-button')).find(
      (button) => button.textContent?.trim() === 'Request a correction',
    );
    expect(requestButton).toBeTruthy();
  });

  it('notes an approved leave request and pre-selects Excused leave for an unmarked student', () => {
    signInWith(Permissions.ATTENDANCE_READ, Permissions.ATTENDANCE_MANAGE);
    fixture = TestBed.createComponent(AttendanceMark);
    fixture.detectChanges();
    httpMock.expectOne(CLASSES_URL).flush(envelope(oneClass()));
    fixture.detectChanges();

    const select = element().querySelector<HTMLSelectElement>('#attendance-section');
    select!.value = 'section-5a';
    select!.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const request = httpMock.expectOne(
      (candidate) => candidate.url === '/api/attendance/sections/section-5a',
    );
    request.flush(
      envelope(
        view({
          entries: [
            {
              studentId: 'student-1',
              admissionNumber: 'ADM-001',
              fullName: 'Aarav Sharma',
              rollNumber: '1',
              editable: true,
              approvedLeave: true,
            },
          ],
        }),
      ),
    );
    fixture.detectChanges();

    expect(text()).toContain('Leave approved');
    const selected = element().querySelector<HTMLButtonElement>('.status-btn--selected');
    expect(selected?.textContent).toContain('Excused leave');
  });
});
