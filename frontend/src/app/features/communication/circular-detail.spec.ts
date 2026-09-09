import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  CircularDetail as CircularDetailModel,
  CircularRecipientResponse,
} from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { CircularDetail } from './circular-detail';

const CIRCULAR_ID = 'circular-1';
const CIRCULAR_URL = `/api/communication/circulars/${CIRCULAR_ID}`;
const RECIPIENTS_URL = `/api/communication/circulars/${CIRCULAR_ID}/recipients`;

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

const page = (content: readonly CircularRecipientResponse[]) => ({
  content,
  page: 0,
  size: 25,
  totalElements: content.length,
  totalPages: 1,
});

const detail = (over: Partial<CircularDetailModel> = {}): CircularDetailModel => ({
  id: CIRCULAR_ID,
  title: 'PTM on Saturday',
  body: 'Please attend the parent-teacher meeting this Saturday at 10am.',
  requiresAcknowledgement: true,
  status: 'PUBLISHED',
  targets: [
    {
      id: 'target-1',
      classId: 'class-5',
      className: 'Class 5',
      sectionId: 'section-5a',
      sectionName: 'A',
    },
  ],
  recipientCount: 1,
  acknowledgedCount: 0,
  publishedAt: '2026-09-06T09:00:00Z',
  createdAt: '2026-09-05T09:00:00Z',
  ...over,
});

const recipient = (over: Partial<CircularRecipientResponse> = {}): CircularRecipientResponse => ({
  id: 'recipient-1',
  studentId: 'student-1',
  studentFullName: 'Aarav Sharma',
  admissionNumber: 'ADM-001',
  sectionId: 'section-5a',
  sectionName: 'A',
  className: 'Class 5',
  deliveredAt: '2026-09-06T09:00:05Z',
  ...over,
});

describe('CircularDetail', () => {
  let fixture: ComponentFixture<CircularDetail>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CircularDetail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('says so when the caller cannot read this circular', () => {
    signInWith();
    fixture = TestBed.createComponent(CircularDetail);
    fixture.componentRef.setInput('id', CIRCULAR_ID);
    fixture.detectChanges();

    httpMock
      .expectOne(CIRCULAR_URL)
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view this circular');
  });

  it('shows the circular, its targets and its recipients', () => {
    signInWith(Permissions.COMMUNICATION_READ);
    fixture = TestBed.createComponent(CircularDetail);
    fixture.componentRef.setInput('id', CIRCULAR_ID);
    fixture.detectChanges();

    httpMock.expectOne(CIRCULAR_URL).flush(envelope(detail()));
    fixture.detectChanges();
    httpMock
      .expectOne((candidate) => candidate.url === RECIPIENTS_URL)
      .flush(envelope(page([recipient()])));
    fixture.detectChanges();

    expect(text()).toContain('PTM on Saturday');
    expect(text()).toContain('Class 5 · A');
    expect(text()).toContain('Aarav Sharma');
    // Acknowledgement is a separate permission this session does not hold.
    expect(text()).not.toContain('Record acknowledgement');
  });

  it("lets a caller with the acknowledge permission record one on a family's behalf", () => {
    signInWith(Permissions.COMMUNICATION_READ, Permissions.COMMUNICATION_ACKNOWLEDGE);
    fixture = TestBed.createComponent(CircularDetail);
    fixture.componentRef.setInput('id', CIRCULAR_ID);
    fixture.detectChanges();

    httpMock.expectOne(CIRCULAR_URL).flush(envelope(detail()));
    fixture.detectChanges();
    httpMock
      .expectOne((candidate) => candidate.url === RECIPIENTS_URL)
      .flush(envelope(page([recipient()])));
    fixture.detectChanges();

    const openButton = Array.from(element().querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Record acknowledgement'),
    ) as HTMLButtonElement;
    expect(openButton).toBeTruthy();
    openButton.click();
    fixture.detectChanges();

    const form = element().querySelector('form');
    expect(form).toBeTruthy();
    form!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    httpMock
      .expectOne((candidate) => candidate.url === `${RECIPIENTS_URL}/recipient-1/acknowledge`)
      .flush(envelope(recipient({ acknowledgedAt: '2026-09-07T10:00:00Z' })));
    fixture.detectChanges();

    expect(text()).toContain("Recorded Aarav Sharma's acknowledgement.");
  });
});
