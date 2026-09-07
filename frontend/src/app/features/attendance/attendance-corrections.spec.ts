import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CorrectionRequestResponse } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { AttendanceCorrections } from './attendance-corrections';

const QUEUE_URL = '/api/attendance/correction-requests';

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

const page = (content: readonly CorrectionRequestResponse[]) => ({
  content,
  page: 0,
  size: 25,
  totalElements: content.length,
  totalPages: 1,
});

const request = (): CorrectionRequestResponse => ({
  id: 'req-1',
  attendanceMarkId: 'mark-1',
  studentId: 'student-1',
  studentName: 'Aarav Sharma',
  attendanceDate: '2026-09-05',
  previousStatus: 'ABSENT',
  requestedStatus: 'PRESENT',
  reason: 'Marked absent by mistake.',
  requestedAt: '2026-09-06T09:00:00Z',
  decision: 'PENDING',
});

describe('AttendanceCorrections', () => {
  let fixture: ComponentFixture<AttendanceCorrections>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AttendanceCorrections],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('says so when the caller cannot approve corrections', () => {
    signInWith();
    fixture = TestBed.createComponent(AttendanceCorrections);
    fixture.detectChanges();

    httpMock
      .expectOne((candidate) => candidate.url === QUEUE_URL)
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to approve attendance corrections');
  });

  it('lists a pending request', () => {
    signInWith(Permissions.ATTENDANCE_CORRECTION_APPROVE);
    fixture = TestBed.createComponent(AttendanceCorrections);
    fixture.detectChanges();

    httpMock
      .expectOne((candidate) => candidate.url === QUEUE_URL)
      .flush(envelope(page([request()])));
    fixture.detectChanges();

    expect(text()).toContain('Aarav Sharma');
    expect(text()).toContain('Marked absent by mistake.');
  });
});
