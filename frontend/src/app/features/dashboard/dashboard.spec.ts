import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Dashboard } from './dashboard';

const URL = '/api/dashboard';

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-07T09:00:00Z',
  traceId: 'test-trace',
  data,
});

/** A principal's view: everything except the audit tile. */
const PRINCIPAL_DASHBOARD = {
  session: {
    set: true,
    sessionId: 'f0000000-0000-7000-8000-000000000001',
    name: '2026-27',
    startsOn: '2026-04-01',
  },
  students: {
    enrolled: 6,
    byClass: [
      { classId: 'c1', className: 'Class 5', sequence: 1, count: 4 },
      { classId: 'c2', className: 'Class 6', sequence: 2, count: 2 },
    ],
  },
  linkageGaps: { studentsWithoutAGuardian: 5, guardiansWithoutAStudent: 1 },
};

/** An auditor's view: only recent activity. */
const AUDITOR_DASHBOARD = {
  recentAudit: {
    events: [
      {
        id: 'e1',
        occurredAt: '2026-09-07T07:15:00Z',
        actorRoles: ['AUDITOR'],
        action: 'LOGIN_SUCCEEDED',
        outcome: 'SUCCESS',
        changedFields: [],
        actorName: 'Sanjay Bhatt',
      },
    ],
  },
};

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const arrive = (data: unknown) => {
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    httpMock.expectOne(URL).flush(envelope(data));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows loading before the first response arrives', () => {
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();

    expect(text()).toContain('Loading');

    httpMock.expectOne(URL).flush(envelope({}));
  });

  it("renders a principal's tiles and never the audit tile they cannot see", () => {
    arrive(PRINCIPAL_DASHBOARD);

    expect(text()).toContain('Academic session');
    expect(text()).toContain('2026-27');
    expect(text()).toContain('Started');

    const values = Array.from(element().querySelectorAll('.tile__value')).map((el) =>
      el.textContent?.trim(),
    );
    expect(values).toContain('6'); // enrolled

    expect(text()).toContain('Class 5');
    expect(text()).toContain('Class 6');
    const counts = Array.from(element().querySelectorAll('.tile__count')).map((el) =>
      el.textContent?.trim(),
    );
    expect(counts).toEqual(expect.arrayContaining(['4', '2', '5', '1']));

    expect(text()).not.toContain('Recent activity');
  });

  it("renders only the auditor's recent-activity tile", () => {
    arrive(AUDITOR_DASHBOARD);

    expect(text()).toContain('Recent activity');
    expect(text()).toContain('Signed in');
    expect(text()).toContain('Sanjay Bhatt');
    expect(text()).not.toContain('Academic session');
    expect(text()).not.toContain('Students enrolled');
    expect(text()).not.toContain('Guardians and students');
  });

  it('says a session has not been set, rather than hiding the tile or showing zeros', () => {
    arrive({ session: { set: false } });

    expect(text()).toContain('No session set yet');
  });

  it('says there is nothing to show rather than rendering an empty page for a bare account', () => {
    arrive({});

    expect(text()).toContain('Nothing here yet for your role');
  });

  it('offers a retry rather than a blank screen when the request fails', () => {
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    httpMock.expectOne(URL).flush(
      {
        success: false,
        timestamp: '2026-09-07T09:00:00Z',
        error: { code: 'SRV_001', message: 'Refused.' },
      },
      { status: 500, statusText: 'Server Error' },
    );
    fixture.detectChanges();

    expect(text()).toContain('Could not load the dashboard');

    const retry = Array.from(element().querySelectorAll('button')).find((candidate) =>
      (candidate.textContent ?? '').includes('Try again'),
    ) as HTMLButtonElement;
    retry.click();
    fixture.detectChanges();

    httpMock.expectOne(URL).flush(envelope(PRINCIPAL_DASHBOARD));
    fixture.detectChanges();
    expect(text()).toContain('2026-27');
  });
});
