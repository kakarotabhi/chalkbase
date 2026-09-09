import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EnquiryDetailResponse } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { EnquiryDetail } from './enquiry-detail';

const ENQUIRY_ID = 'enq-1';
const RECORD_URL = `/api/admissions/enquiries/${ENQUIRY_ID}`;
const COUNSELLORS_URL = '/api/admissions/counsellors';

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

const detail = (over: Partial<EnquiryDetailResponse> = {}): EnquiryDetailResponse => ({
  id: ENQUIRY_ID,
  childFullName: 'Aarav Sharma',
  parentName: 'Rohit Sharma',
  parentPhone: '9000000001',
  source: 'WALK_IN',
  status: 'IN_PROGRESS',
  assignedCounsellorId: 'user-1',
  assignedCounsellorName: 'Test Counsellor',
  nextFollowUpDate: '2026-09-10',
  capturedByName: 'Front Office',
  createdAt: '2026-09-08T09:00:00Z',
  updatedAt: '2026-09-08T09:00:00Z',
  followUps: [
    {
      id: 'fu-1',
      note: 'Called, asked to visit next week.',
      nextFollowUpDate: '2026-09-10',
      recordedByName: 'Test Counsellor',
      recordedAt: '2026-09-08T09:30:00Z',
    },
  ],
  ...over,
});

describe('EnquiryDetail', () => {
  let fixture: ComponentFixture<EnquiryDetail>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnquiryDetail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function create(): void {
    fixture = TestBed.createComponent(EnquiryDetail);
    fixture.componentRef.setInput('id', ENQUIRY_ID);
    fixture.detectChanges();
  }

  it('says so when the caller cannot see this enquiry', () => {
    signInWith();
    create();

    httpMock.expectOne(COUNSELLORS_URL).flush(envelope([]));
    httpMock
      .expectOne(RECORD_URL)
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view this enquiry');
  });

  it('says so for an enquiry that does not exist at this school', () => {
    signInWith(Permissions.ADMISSION_ENQUIRY_READ);
    create();

    httpMock.expectOne(COUNSELLORS_URL).flush(envelope([]));
    httpMock
      .expectOne(RECORD_URL)
      .flush(refusal('NF_001'), { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(text()).toContain('There is no such enquiry at this school');
  });

  it('shows the enquiry and its follow-up history', () => {
    signInWith(Permissions.ADMISSION_ENQUIRY_READ);
    create();

    httpMock.expectOne(COUNSELLORS_URL).flush(envelope([]));
    httpMock.expectOne(RECORD_URL).flush(envelope(detail()));
    fixture.detectChanges();

    expect(text()).toContain('Aarav Sharma');
    expect(text()).toContain('Test Counsellor');
    expect(text()).toContain('Called, asked to visit next week.');
  });

  it('hides the follow-up form once the enquiry has closed', () => {
    signInWith(Permissions.ADMISSION_ENQUIRY_READ, Permissions.ADMISSION_ENQUIRY_MANAGE);
    create();

    httpMock.expectOne(COUNSELLORS_URL).flush(envelope([]));
    httpMock
      .expectOne(RECORD_URL)
      .flush(envelope(detail({ status: 'CONVERTED', nextFollowUpDate: undefined })));
    fixture.detectChanges();

    expect(text()).toContain('nothing left to follow up');
    expect(element().querySelector('#follow-up-note')).toBeNull();
  });

  it('offers to reassign the counsellor to someone who may manage enquiries', () => {
    signInWith(Permissions.ADMISSION_ENQUIRY_READ, Permissions.ADMISSION_ENQUIRY_MANAGE);
    create();

    httpMock.expectOne(COUNSELLORS_URL).flush(envelope([]));
    httpMock.expectOne(RECORD_URL).flush(envelope(detail()));
    fixture.detectChanges();

    const buttons = Array.from(element().querySelectorAll('button')).map((b) =>
      b.textContent?.trim(),
    );
    expect(buttons).toContain('Reassign');
  });
});
