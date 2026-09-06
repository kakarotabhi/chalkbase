import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { GuardianSummary } from '../../core/api/models';
import { GuardianList } from './guardian-list';

const GUARDIANS = '/api/guardians';

/**
 * Invented families at an invented school.
 *
 * No `email` key, deliberately: the backend serialises with `default-property-inclusion: non_null`,
 * so a guardian without one arrives with the field absent rather than as `email: null`.
 */
const guardian = (over: Partial<GuardianSummary> = {}): GuardianSummary => ({
  id: 'g-1',
  fullName: 'Test Parent One',
  phone: '9000000001',
  occupation: 'Shopkeeper',
  linkedStudentCount: 4,
  ...over,
});

const page = (content: readonly GuardianSummary[], totalElements = content.length) => ({
  content,
  page: 0,
  size: 25,
  totalElements,
  totalPages: Math.max(1, Math.ceil(totalElements / 25)),
});

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-06T10:00:00Z',
  traceId: 'test-trace',
  data,
});

const refusal = (code: string) => ({
  success: false,
  timestamp: '2026-09-06T10:00:00Z',
  error: { code, message: 'Refused.' },
});

describe('GuardianList', () => {
  let fixture: ComponentFixture<GuardianList>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const search = () => httpMock.expectOne((request) => request.url === GUARDIANS);

  const button = (label: string) =>
    Array.from(element().querySelectorAll('button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement;

  const type = (id: string, value: string) => {
    const input = element().querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const arrive = (rows: readonly GuardianSummary[] = [guardian()]) => {
    fixture = TestBed.createComponent(GuardianList);
    fixture.detectChanges();
    search().flush(envelope(page(rows)));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GuardianList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  /**
   * The count is the whole reason this screen exists rather than a phone-number box on each child:
   * it says that editing this row reaches every student the guardian is attached to.
   */
  it('shows each guardian once, with how many students they are linked to', () => {
    arrive();

    expect(text()).toContain('Test Parent One');
    expect(text()).toContain('9000000001');
    expect(text()).toContain('Linked to 4 students');
    expect(text()).toContain('correcting their phone number corrects it for all four');
  });

  /** Absent, not null — see the fixture. */
  it('says a field is not recorded when the server left it out', () => {
    arrive();

    expect(text()).toContain('Not recorded');
  });

  /**
   * ADR-0020 §6. A link is ended from the student's own record, where you can see whose it is; a
   * button here would end a relationship without showing which child it was about.
   */
  it('offers no way to delete a guardian, and no way to unlink one', () => {
    arrive();

    const labels = Array.from(element().querySelectorAll('button')).map((candidate) =>
      (candidate.textContent ?? '').trim().toLowerCase(),
    );
    expect(labels.some((label) => label.includes('delete'))).toBe(false);
    expect(labels.some((label) => label.includes('remove'))).toBe(false);
    expect(labels).toContain('edit');
  });

  it('explains a 403 rather than crashing', () => {
    fixture = TestBed.createComponent(GuardianList);
    fixture.detectChanges();
    search().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view guardians');
    expect(element().querySelector('#guardian-search')).toBeNull();
  });

  it('offers a retry when the list fails for any other reason', () => {
    fixture = TestBed.createComponent(GuardianList);
    fixture.detectChanges();
    search().flush(refusal('GEN_001'), { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not load the guardians');

    button('Try again').click();
    fixture.detectChanges();
    search().flush(envelope(page([guardian()])));
    fixture.detectChanges();

    expect(text()).toContain('Test Parent One');
  });

  // ── Editing ──────────────────────────────────────────────────────────────────────────────

  /**
   * Editing here is the point of the model working: one record, one correction, and every child
   * of theirs reads the new number. The screen says so when it has done it.
   */
  it('saves a correction once, for every student the guardian is linked to', () => {
    arrive();

    button('Edit').click();
    fixture.detectChanges();

    type('guardian-phone', '9000000009');
    button('Save guardian').click();
    fixture.detectChanges();

    const request = httpMock.expectOne({ url: `${GUARDIANS}/g-1`, method: 'PUT' });
    expect(request.request.body).toEqual({
      fullName: 'Test Parent One',
      phone: '9000000009',
      email: '',
      occupation: 'Shopkeeper',
    });
    request.flush(envelope(null));
    fixture.detectChanges();

    // The page is re-read rather than patched: `linkedStudentCount` is computed per row.
    search().flush(envelope(page([guardian({ phone: '9000000009' })])));
    fixture.detectChanges();

    expect(text()).toContain('for every student they are linked to');
    expect(text()).toContain('9000000009');
  });

  /**
   * The second place a duplicate can be born — the first is the attach panel on a student's
   * record — so the same warning is said here, at the moment of creating.
   */
  it('tells the user to search before adding a new guardian', () => {
    arrive();

    button('Add a guardian').click();
    fixture.detectChanges();

    expect(text()).toContain('Search above first');
    expect(text()).toContain('rather than adding a second one');
  });

  it('refuses a guardian with no name, before asking the server', () => {
    arrive();

    button('Add a guardian').click();
    fixture.detectChanges();
    button('Save guardian').click();
    fixture.detectChanges();

    expect(text()).toContain("Enter the guardian's name");
    // Nothing was sent: `httpMock.verify()` in afterEach is what asserts that.
  });

  it('creates a guardian and re-reads the page', () => {
    arrive();

    button('Add a guardian').click();
    fixture.detectChanges();

    type('guardian-name', 'Test Parent Two');
    type('guardian-phone', '9000000002');
    button('Save guardian').click();
    fixture.detectChanges();

    const request = httpMock.expectOne({ url: GUARDIANS, method: 'POST' });
    expect(request.request.body).toEqual({
      fullName: 'Test Parent Two',
      phone: '9000000002',
      email: '',
      occupation: '',
    });
    request.flush(envelope(guardian({ id: 'g-2', fullName: 'Test Parent Two' })));
    fixture.detectChanges();

    search().flush(
      envelope(page([guardian(), guardian({ id: 'g-2', fullName: 'Test Parent Two' })])),
    );
    fixture.detectChanges();

    expect(text()).toContain('Test Parent Two was added');
  });
});
