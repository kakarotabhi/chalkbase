import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LeaveRequestResponse } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { LeaveRequestDetail } from './leave-request-detail';

const LEAVE_ID = 'leave-1';
const DETAIL_URL = `/api/attendance/leave-requests/${LEAVE_ID}`;
const DECISION_URL = `/api/attendance/leave-requests/${LEAVE_ID}/decision`;

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

const pendingRequest = (): LeaveRequestResponse => ({
  id: LEAVE_ID,
  studentId: 'student-1',
  studentName: 'Aarav Sharma',
  sectionId: 'section-5a',
  sectionName: 'A',
  className: 'Class 5',
  startDate: '2026-09-10',
  endDate: '2026-09-12',
  reason: 'Family function out of town.',
  requestedAt: '2026-09-09T09:00:00Z',
  decision: 'PENDING',
});

describe('LeaveRequestDetail', () => {
  let fixture: ComponentFixture<LeaveRequestDetail>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LeaveRequestDetail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('says so when the caller cannot view this leave request', () => {
    signInWith();
    fixture = TestBed.createComponent(LeaveRequestDetail);
    fixture.componentRef.setInput('id', LEAVE_ID);
    fixture.detectChanges();

    httpMock
      .expectOne(DETAIL_URL)
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view leave requests');
  });

  it('shows the request and lets an approver decide it', () => {
    signInWith(Permissions.ATTENDANCE_LEAVE_READ, Permissions.ATTENDANCE_LEAVE_APPROVE);
    fixture = TestBed.createComponent(LeaveRequestDetail);
    fixture.componentRef.setInput('id', LEAVE_ID);
    fixture.detectChanges();

    httpMock.expectOne(DETAIL_URL).flush(envelope(pendingRequest()));
    fixture.detectChanges();

    expect(text()).toContain('Aarav Sharma');
    expect(text()).toContain('Family function out of town.');

    const approve = Array.from(element().querySelectorAll('cb-button button')).find(
      (candidate) => (candidate.textContent ?? '').trim() === 'Approve',
    ) as HTMLButtonElement;
    approve.click();
    fixture.detectChanges();

    const decision = httpMock.expectOne(DECISION_URL);
    expect(decision.request.body).toEqual({ decision: 'APPROVED' });
    decision.flush(
      envelope({ ...pendingRequest(), decision: 'APPROVED', decidedAt: '2026-09-09T10:05:00Z' }),
    );
    fixture.detectChanges();

    expect(text()).toContain('Leave request approved.');
  });

  it('renders the date range and the decision timestamp for a school reader, not raw machine values', () => {
    signInWith(Permissions.ATTENDANCE_LEAVE_READ, Permissions.ATTENDANCE_LEAVE_APPROVE);
    fixture = TestBed.createComponent(LeaveRequestDetail);
    fixture.componentRef.setInput('id', LEAVE_ID);
    fixture.detectChanges();

    httpMock.expectOne(DETAIL_URL).flush(
      envelope({
        ...pendingRequest(),
        decision: 'APPROVED',
        decidedAt: '2026-09-09T05:48:44.543297687Z',
      }),
    );
    fixture.detectChanges();

    // The session fixture's school is Asia/Kolkata (session-fixture.ts): 05:48 UTC is 11:18 there.
    expect(text()).toContain('10 Sept 2026 – 12 Sept 2026');
    expect(text()).toContain('9 Sept 2026, 11:18');
    expect(text()).not.toContain('2026-09-10');
    expect(text()).not.toContain('2026-09-09T05:48:44.543297687Z');
  });

  it('does not offer a decision to a caller who can only read', () => {
    signInWith(Permissions.ATTENDANCE_LEAVE_READ);
    fixture = TestBed.createComponent(LeaveRequestDetail);
    fixture.componentRef.setInput('id', LEAVE_ID);
    fixture.detectChanges();

    httpMock.expectOne(DETAIL_URL).flush(envelope(pendingRequest()));
    fixture.detectChanges();

    expect(text()).not.toContain('Approve');
  });
});
