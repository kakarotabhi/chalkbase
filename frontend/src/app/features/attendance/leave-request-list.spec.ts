import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { LeaveRequestResponse } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { LeaveRequestList } from './leave-request-list';

const LIST_URL = '/api/attendance/leave-requests';

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

const page = (content: readonly LeaveRequestResponse[]) => ({
  content,
  page: 0,
  size: 25,
  totalElements: content.length,
  totalPages: 1,
});

const request = (): LeaveRequestResponse => ({
  id: 'leave-1',
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

describe('LeaveRequestList', () => {
  let fixture: ComponentFixture<LeaveRequestList>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LeaveRequestList],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  it('says so when the caller cannot see leave requests', () => {
    signInWith();
    fixture = TestBed.createComponent(LeaveRequestList);
    fixture.detectChanges();

    httpMock
      .expectOne((candidate) => candidate.url === LIST_URL)
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view leave requests');
  });

  it('lists a request and offers filing a new one when permitted', () => {
    signInWith(Permissions.ATTENDANCE_LEAVE_READ, Permissions.ATTENDANCE_LEAVE_REQUEST);
    fixture = TestBed.createComponent(LeaveRequestList);
    fixture.detectChanges();

    httpMock
      .expectOne((candidate) => candidate.url === LIST_URL)
      .flush(envelope(page([request()])));
    fixture.detectChanges();

    expect(text()).toContain('Aarav Sharma');
    expect(text()).toContain('Class 5 · A');
    expect(text()).toContain('New leave request');
  });

  it('renders the date range as a school reads it, not the raw yyyy-MM-dd values', () => {
    signInWith(Permissions.ATTENDANCE_LEAVE_READ);
    fixture = TestBed.createComponent(LeaveRequestList);
    fixture.detectChanges();

    httpMock
      .expectOne((candidate) => candidate.url === LIST_URL)
      .flush(envelope(page([request()])));
    fixture.detectChanges();

    expect(text()).toContain('10 Sept 2026 – 12 Sept 2026');
    expect(text()).not.toContain('2026-09-10');
    expect(text()).not.toContain('2026-09-12');
  });

  it('hides "New leave request" for a caller who cannot file one', () => {
    signInWith(Permissions.ATTENDANCE_LEAVE_READ);
    fixture = TestBed.createComponent(LeaveRequestList);
    fixture.detectChanges();

    httpMock.expectOne((candidate) => candidate.url === LIST_URL).flush(envelope(page([])));
    fixture.detectChanges();

    expect(text()).not.toContain('New leave request');
  });

  // ── The URL (Finding E) ──────────────────────────────────────────────────────────────────

  it('mirrors a status filter change into the URL, replacing rather than pushing', () => {
    signInWith(Permissions.ATTENDANCE_LEAVE_READ);
    fixture = TestBed.createComponent(LeaveRequestList);
    fixture.detectChanges();
    httpMock.expectOne((candidate) => candidate.url === LIST_URL).flush(envelope(page([])));
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const select = element().querySelector('#leave-status-filter') as HTMLSelectElement;
    select.value = 'APPROVED';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: { decision: 'APPROVED', page: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      }),
    );

    httpMock.expectOne((candidate) => candidate.url === LIST_URL).flush(envelope(page([])));
  });

  /** Loading a link with `?decision=&page=` reproduces that filtered, paged view. */
  it('loads a URL with a status filter and a page by reproducing that view', () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: {
        snapshot: { queryParamMap: convertToParamMap({ decision: 'REJECTED', page: '1' }) },
      },
    });
    signInWith(Permissions.ATTENDANCE_LEAVE_READ);

    fixture = TestBed.createComponent(LeaveRequestList);
    fixture.detectChanges();

    const httpRequest = httpMock.expectOne((candidate) => candidate.url === LIST_URL);
    expect(httpRequest.request.params.get('decision')).toBe('REJECTED');
    expect(httpRequest.request.params.get('page')).toBe('1');
    httpRequest.flush(envelope(page([])));
    fixture.detectChanges();

    expect((element().querySelector('#leave-status-filter') as HTMLSelectElement).value).toBe(
      'REJECTED',
    );
  });
});
