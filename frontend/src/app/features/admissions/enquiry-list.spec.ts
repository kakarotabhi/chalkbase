import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { EnquirySummary } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { EnquiryList } from './enquiry-list';

const ENQUIRIES_URL = '/api/admissions/enquiries';
const CLASSES_URL = '/api/academics/classes';
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

const page = (content: readonly EnquirySummary[]) => ({
  content,
  page: 0,
  size: 25,
  totalElements: content.length,
  totalPages: 1,
});

const enquiry = (over: Partial<EnquirySummary> = {}): EnquirySummary => ({
  id: 'enq-1',
  childFullName: 'Aarav Sharma',
  parentName: 'Rohit Sharma',
  parentPhone: '9000000001',
  source: 'WALK_IN',
  status: 'NEW',
  assignedCounsellorName: 'Test Counsellor',
  nextFollowUpDate: '2026-09-09',
  createdAt: '2026-09-09T09:00:00Z',
  ...over,
});

describe('EnquiryList', () => {
  let fixture: ComponentFixture<EnquiryList>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnquiryList],
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

  function flushReferenceData(): void {
    httpMock.expectOne(CLASSES_URL).flush(envelope([]));
    httpMock.expectOne(COUNSELLORS_URL).flush(envelope([]));
  }

  it('says so when the caller cannot see enquiries', () => {
    signInWith();
    fixture = TestBed.createComponent(EnquiryList);
    fixture.detectChanges();

    flushReferenceData();
    httpMock
      .expectOne((candidate) => candidate.url === ENQUIRIES_URL)
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view enquiries');
  });

  it('lists an enquiry, with its counsellor and next follow-up date', () => {
    signInWith(Permissions.ADMISSION_ENQUIRY_READ);
    fixture = TestBed.createComponent(EnquiryList);
    fixture.detectChanges();

    flushReferenceData();
    httpMock
      .expectOne((candidate) => candidate.url === ENQUIRIES_URL)
      .flush(envelope(page([enquiry()])));
    fixture.detectChanges();

    expect(text()).toContain('Aarav Sharma');
    expect(text()).toContain('Rohit Sharma');
    expect(text()).toContain('Test Counsellor');
    expect(text()).toContain('New');
  });

  it('offers "Add enquiry" only to someone who may manage enquiries', () => {
    signInWith(Permissions.ADMISSION_ENQUIRY_READ);
    fixture = TestBed.createComponent(EnquiryList);
    fixture.detectChanges();

    flushReferenceData();
    httpMock.expectOne((candidate) => candidate.url === ENQUIRIES_URL).flush(envelope(page([])));
    fixture.detectChanges();

    expect(element().querySelector('#enquiry-add')).toBeNull();
  });

  it('opens the capture form for someone who may manage enquiries', () => {
    signInWith(Permissions.ADMISSION_ENQUIRY_READ, Permissions.ADMISSION_ENQUIRY_MANAGE);
    fixture = TestBed.createComponent(EnquiryList);
    fixture.detectChanges();

    flushReferenceData();
    httpMock.expectOne((candidate) => candidate.url === ENQUIRIES_URL).flush(envelope(page([])));
    fixture.detectChanges();

    const addButton = element().querySelector<HTMLButtonElement>('#enquiry-add');
    expect(addButton).toBeTruthy();
    addButton!.click();
    fixture.detectChanges();

    expect(text()).toContain('Capture an enquiry');
  });

  // ── The URL (Finding E) ──────────────────────────────────────────────────────────────────

  /**
   * `status` is safe to carry in a link — it names no one. `q` (a child's or parent's name) stays
   * out, per the class Javadoc, exactly as `student-list` keeps its own search box out.
   */
  it('mirrors a status filter change into the URL, replacing rather than pushing', () => {
    signInWith(Permissions.ADMISSION_ENQUIRY_READ);
    fixture = TestBed.createComponent(EnquiryList);
    fixture.detectChanges();
    flushReferenceData();
    httpMock.expectOne((candidate) => candidate.url === ENQUIRIES_URL).flush(envelope(page([])));
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const select = element().querySelector('#enquiry-status-filter') as HTMLSelectElement;
    select.value = 'CONVERTED';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({
        queryParams: {
          status: 'CONVERTED',
          source: null,
          assignedCounsellorId: null,
          page: null,
        },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      }),
    );

    httpMock.expectOne((candidate) => candidate.url === ENQUIRIES_URL).flush(envelope(page([])));
  });

  /** Loading a link with `?status=&source=&page=` reproduces that filtered, paged view. */
  it('loads a URL with query params by reproducing that filtered, paged view', async () => {
    // `overrideProvider` cannot follow the `TestBed.inject` in `beforeEach` — the module is
    // already instantiated by then — so this one test builds its own, with the route it needs.
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [EnquiryList],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({
                status: 'CONVERTED',
                source: 'REFERRAL',
                page: '2',
              }),
            },
          },
        },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    signInWith(Permissions.ADMISSION_ENQUIRY_READ);

    fixture = TestBed.createComponent(EnquiryList);
    fixture.detectChanges();
    flushReferenceData();

    const request = httpMock.expectOne((candidate) => candidate.url === ENQUIRIES_URL);
    expect(request.request.params.get('status')).toBe('CONVERTED');
    expect(request.request.params.get('source')).toBe('REFERRAL');
    expect(request.request.params.get('page')).toBe('2');
    request.flush(envelope(page([])));
    fixture.detectChanges();

    expect((element().querySelector('#enquiry-status-filter') as HTMLSelectElement).value).toBe(
      'CONVERTED',
    );
    expect((element().querySelector('#enquiry-source-filter') as HTMLSelectElement).value).toBe(
      'REFERRAL',
    );
  });
});
