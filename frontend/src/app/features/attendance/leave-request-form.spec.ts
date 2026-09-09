import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { SchoolClass, SectionAttendanceView } from '../../core/api/models';
import { signInWith } from '../../core/auth/session-fixture';
import { LeaveRequestForm } from './leave-request-form';

const CLASSES_URL = '/api/academics/classes';
const ROSTER_URL = '/api/attendance/sections/section-5a';
const CREATE_URL = '/api/attendance/sections/section-5a/leave-requests';

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-09T10:00:00Z',
  traceId: 'test-trace',
  data,
});

const refusal = (code: string) => ({
  success: false,
  timestamp: '2026-09-09T10:00:00Z',
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

const roster = (): SectionAttendanceView => ({
  sectionId: 'section-5a',
  sectionName: 'A',
  className: 'Class 5',
  academicSessionId: 'session-1',
  attendanceDate: '2026-09-09',
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
});

describe('LeaveRequestForm', () => {
  let fixture: ComponentFixture<LeaveRequestForm>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;

  const chooseSection = () => {
    const select = element().querySelector<HTMLSelectElement>('#leave-section');
    select!.value = 'section-5a';
    select!.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    httpMock.expectOne((candidate) => candidate.url === ROSTER_URL).flush(envelope(roster()));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LeaveRequestForm],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('files a leave request and lands on its detail page', () => {
    signInWith();
    fixture = TestBed.createComponent(LeaveRequestForm);
    fixture.detectChanges();
    httpMock.expectOne(CLASSES_URL).flush(envelope(oneClass()));
    fixture.detectChanges();

    chooseSection();

    const studentSelect = element().querySelector<HTMLSelectElement>('#leave-student');
    studentSelect!.value = 'student-1';
    studentSelect!.dispatchEvent(new Event('change'));

    const reason = element().querySelector<HTMLTextAreaElement>('#leave-reason');
    reason!.value = 'Family function out of town.';
    reason!.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate');

    const form = element().querySelector('form')!;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const created = httpMock.expectOne((candidate) => candidate.url === CREATE_URL);
    expect(created.request.body).toEqual({
      studentId: 'student-1',
      startDate: expect.any(String),
      endDate: expect.any(String),
      reason: 'Family function out of town.',
    });
    created.flush(
      envelope({
        id: 'leave-1',
        studentId: 'student-1',
        studentName: 'Aarav Sharma',
        sectionId: 'section-5a',
        sectionName: 'A',
        className: 'Class 5',
        startDate: '2026-09-10',
        endDate: '2026-09-10',
        reason: 'Family function out of town.',
        requestedAt: '2026-09-09T09:00:00Z',
        decision: 'PENDING',
      }),
    );
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenCalledWith(['/attendance/leave', 'leave-1']);
  });

  it('explains a backdated date instead of a bare error code', () => {
    signInWith();
    fixture = TestBed.createComponent(LeaveRequestForm);
    fixture.detectChanges();
    httpMock.expectOne(CLASSES_URL).flush(envelope(oneClass()));
    fixture.detectChanges();

    chooseSection();

    const studentSelect = element().querySelector<HTMLSelectElement>('#leave-student');
    studentSelect!.value = 'student-1';
    studentSelect!.dispatchEvent(new Event('change'));
    const reason = element().querySelector<HTMLTextAreaElement>('#leave-reason');
    reason!.value = 'Was away.';
    reason!.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form = element().querySelector('form')!;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    httpMock
      .expectOne((candidate) => candidate.url === CREATE_URL)
      .flush(refusal('ATT_010'), { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(element().textContent ?? '').toContain('must start today or later');
  });
});
