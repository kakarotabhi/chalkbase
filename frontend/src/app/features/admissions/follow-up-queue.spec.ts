import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EnquiryFollowUpQueueItem } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { FollowUpQueue } from './follow-up-queue';

const QUEUE_URL = '/api/admissions/follow-ups/due';

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

const page = (content: readonly EnquiryFollowUpQueueItem[]) => ({
  content,
  page: 0,
  size: 25,
  totalElements: content.length,
  totalPages: 1,
});

const item = (over: Partial<EnquiryFollowUpQueueItem> = {}): EnquiryFollowUpQueueItem => ({
  enquiryId: 'enq-1',
  childFullName: 'Aarav Sharma',
  parentName: 'Rohit Sharma',
  parentPhone: '9000000001',
  status: 'IN_PROGRESS',
  assignedCounsellorName: 'Test Counsellor',
  nextFollowUpDate: '2026-09-08',
  overdue: true,
  ...over,
});

describe('FollowUpQueue', () => {
  let fixture: ComponentFixture<FollowUpQueue>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FollowUpQueue],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it("defaults to the caller's own enquiries", () => {
    signInWith(Permissions.ADMISSION_ENQUIRY_READ);
    fixture = TestBed.createComponent(FollowUpQueue);
    fixture.detectChanges();

    const request = httpMock.expectOne(
      (candidate) => candidate.url === QUEUE_URL && candidate.params.get('mine') === 'true',
    );
    request.flush(envelope(page([item()])));
    fixture.detectChanges();

    expect(text()).toContain('Aarav Sharma');
    expect(text()).toContain('Overdue');
  });

  it('says so when the caller cannot see the queue', () => {
    signInWith();
    fixture = TestBed.createComponent(FollowUpQueue);
    fixture.detectChanges();

    httpMock
      .expectOne((candidate) => candidate.url === QUEUE_URL)
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view the follow-up queue');
  });

  it('says so when nothing is due', () => {
    signInWith(Permissions.ADMISSION_ENQUIRY_READ);
    fixture = TestBed.createComponent(FollowUpQueue);
    fixture.detectChanges();

    httpMock.expectOne((candidate) => candidate.url === QUEUE_URL).flush(envelope(page([])));
    fixture.detectChanges();

    expect(text()).toContain('Nothing due right now');
  });

  it("switches to every counsellor's queue", () => {
    signInWith(Permissions.ADMISSION_ENQUIRY_READ);
    fixture = TestBed.createComponent(FollowUpQueue);
    fixture.detectChanges();

    httpMock.expectOne((candidate) => candidate.url === QUEUE_URL).flush(envelope(page([])));
    fixture.detectChanges();

    const select = element().querySelector<HTMLSelectElement>('#queue-scope');
    select!.value = 'all';
    select!.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const request = httpMock.expectOne(
      (candidate) => candidate.url === QUEUE_URL && candidate.params.get('mine') === 'false',
    );
    request.flush(envelope(page([item({ assignedCounsellorName: 'Another Counsellor' })])));
    fixture.detectChanges();

    expect(text()).toContain('Another Counsellor');
  });
});
