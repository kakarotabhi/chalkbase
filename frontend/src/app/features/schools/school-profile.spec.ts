import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { unsavedChangesGuard } from '../../core/forms/unsaved-changes-guard';
import { SchoolProfile } from './school-profile';

const URL = '/api/school/profile';

/** An invented school. Never real school, staff or student data in a fixture. */
const SAVED_PROFILE = {
  code: 'EVG-101',
  schemaName: 'evergreen',
  name: 'Evergreen Public School',
  board: 'CBSE',
  addressLine1: 'Plot 14, Baner Road',
  addressLine2: 'Near the water tower',
  city: 'Pune',
  state: 'Maharashtra',
  pincode: '411045',
  principalName: 'Meera Iyer',
  phone: '+91 20 2721 0000',
  email: 'office@evergreen.example',
  website: 'https://evergreen.example',
  affiliationNumber: '1130456',
  configured: true,
  updatedAt: '2026-09-06T09:00:00Z',
};

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-06T10:00:00Z',
  traceId: 'test-trace',
  data,
});

describe('SchoolProfile', () => {
  let fixture: ComponentFixture<SchoolProfile>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';
  const field = (id: string) => element().querySelector(`#${id}`) as HTMLInputElement;
  const picker = (id: string) => element().querySelector(`#${id}`) as HTMLSelectElement;
  const button = (label: string) =>
    Array.from(element().querySelectorAll('cb-button button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement;

  const type = (id: string, value: string) => {
    const input = field(id);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const submit = () => {
    element().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };

  /** Creates the screen and answers its one bootstrap request. */
  const arrive = (profile: unknown = SAVED_PROFILE) => {
    fixture = TestBed.createComponent(SchoolProfile);
    httpMock.expectOne(URL).flush(envelope(profile));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SchoolProfile],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  // ── Loading ──────────────────────────────────────────────────────────────────────────────

  it('shows the school the API returned', () => {
    arrive();

    expect(field('school-name').value).toBe('Evergreen Public School');
    expect(field('school-address1').value).toBe('Plot 14, Baner Road');
    expect(field('school-city').value).toBe('Pune');
    expect(field('school-pincode').value).toBe('411045');
    expect(field('school-principal').value).toBe('Meera Iyer');
    expect(field('school-email').value).toBe('office@evergreen.example');
    expect(picker('school-board').value).toBe('CBSE');
    expect(picker('school-state').value).toBe('Maharashtra');
  });

  /** The code addresses the tenant (ADR-0011). It is shown so it can be read, never edited. */
  it('shows the school code but does not let it be edited', () => {
    arrive();

    expect(field('school-code').value).toBe('EVG-101');
    expect(field('school-code').disabled).toBe(true);
  });

  it('says so when the school has never filled its profile in', () => {
    arrive({
      code: 'EVG-101',
      schemaName: 'evergreen',
      name: 'Evergreen Public School',
      board: 'CBSE',
      city: 'Pune',
      state: 'Maharashtra',
      configured: false,
    });

    expect(text()).toContain("This school's profile is not filled in yet");
    // The registry's own details are seeded in rather than asked for twice.
    expect(field('school-name').value).toBe('Evergreen Public School');
    expect(field('school-address1').value).toBe('');
  });

  it('offers a retry when the profile cannot be loaded', () => {
    fixture = TestBed.createComponent(SchoolProfile);
    httpMock.expectOne(URL).flush(
      {
        success: false,
        timestamp: '2026-09-06T10:00:00Z',
        error: { code: 'GEN_001', message: 'Broken.' },
      },
      { status: 500, statusText: 'Internal Server Error' },
    );
    fixture.detectChanges();

    expect(text()).toContain('Could not load the school profile');

    button('Try again').click();
    fixture.detectChanges();
    httpMock.expectOne(URL).flush(envelope(SAVED_PROFILE));
    fixture.detectChanges();

    expect(field('school-city').value).toBe('Pune');
  });

  // ── Validation ───────────────────────────────────────────────────────────────────────────

  it('puts a validation message under the field it belongs to, and sends nothing', () => {
    arrive();

    type('school-pincode', '011045');
    submit();

    const error = element().querySelector('#school-pincode-error');
    expect(error?.textContent).toContain('A PIN code is six digits');
    expect(field('school-pincode').getAttribute('aria-describedby')).toBe('school-pincode-error');
    expect(field('school-pincode').getAttribute('aria-invalid')).toBe('true');
    // The one field that is wrong is the one that is marked.
    expect(element().querySelector('#school-city-error')).toBeNull();

    httpMock.expectNone(URL);
  });

  it('refuses to save with a required field emptied', () => {
    arrive();

    type('school-city', '');
    submit();

    expect(element().querySelector('#school-city-error')?.textContent).toContain(
      'Enter the city or town.',
    );
    httpMock.expectNone(URL);
  });

  /** Whatever the server rejects, the form has to be able to show against the right field. */
  it('attaches a rejected field from the server to that field', () => {
    arrive();

    type('school-phone', '+91 20 2721 0001');
    submit();

    httpMock.expectOne(URL).flush(
      {
        success: false,
        timestamp: '2026-09-06T10:00:00Z',
        error: {
          code: 'VAL_001',
          message: 'Some of the information provided is not valid',
          details: { phone: 'must be 7 to 20 characters of digits, spaces, brackets or dashes' },
        },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(element().querySelector('#school-phone-error')?.textContent).toContain(
      'must be 7 to 20 characters',
    );
    expect(text()).toContain('Some of these details were refused');
  });

  it('explains a refusal to change the school code without blaming the user', () => {
    arrive();

    type('school-city', 'Nashik');
    submit();

    httpMock.expectOne(URL).flush(
      {
        success: false,
        timestamp: '2026-09-06T10:00:00Z',
        error: { code: 'SCHOOL_002', message: 'The school code and schema name cannot be changed' },
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    fixture.detectChanges();

    expect(text()).toContain('The school code cannot be changed');
  });

  // ── The unsaved-changes bar ──────────────────────────────────────────────────────────────

  it('shows nothing to save until something changes', () => {
    arrive();

    expect(text()).not.toContain('Unsaved changes');
    expect(button('Save changes').disabled).toBe(true);
  });

  it('raises the unsaved-changes bar on the first edit and lowers it after a save', () => {
    arrive();

    type('school-city', 'Nashik');
    expect(text()).toContain('Unsaved changes');
    expect(button('Save changes').disabled).toBe(false);

    submit();
    const request = httpMock.expectOne(URL);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toMatchObject({
      code: 'EVG-101',
      schemaName: 'evergreen',
      city: 'Nashik',
      name: 'Evergreen Public School',
    });

    request.flush(envelope({ ...SAVED_PROFILE, city: 'Nashik' }));
    fixture.detectChanges();

    expect(text()).not.toContain('Unsaved changes');
    expect(text()).toContain('School profile saved');
    expect(field('school-city').value).toBe('Nashik');
  });

  it('puts an edit back when the change is cancelled', () => {
    arrive();

    type('school-city', 'Nashik');
    button('Cancel').click();
    fixture.detectChanges();

    expect(field('school-city').value).toBe('Pune');
    expect(text()).not.toContain('Unsaved changes');
    httpMock.expectNone(URL);
  });

  // ── Saving ───────────────────────────────────────────────────────────────────────────────

  it('makes everything read-only while the save is in flight', () => {
    arrive();

    type('school-city', 'Nashik');
    submit();

    expect(field('school-name').disabled).toBe(true);
    expect(field('school-city').disabled).toBe(true);
    expect(picker('school-board').disabled).toBe(true);
    expect(button('Cancel').disabled).toBe(true);
    expect(button('Saving…').getAttribute('aria-busy')).toBe('true');
    expect(button('Saving…').disabled).toBe(true);

    httpMock.expectOne(URL).flush(envelope(SAVED_PROFILE));
    fixture.detectChanges();

    expect(field('school-city').disabled).toBe(false);
  });

  /** A second submit while the first is in flight would save twice and race its own answer. */
  it('will not start a second save while one is in flight', () => {
    arrive();

    type('school-city', 'Nashik');
    submit();
    submit();

    httpMock.expectOne(URL).flush(envelope({ ...SAVED_PROFILE, city: 'Nashik' }));
    fixture.detectChanges();
  });

  // ── Leaving ──────────────────────────────────────────────────────────────────────────────

  it('asks before leaving with unsaved changes, and stays when the answer is no', () => {
    arrive();
    const confirmed = vi.spyOn(window, 'confirm').mockReturnValue(false);

    type('school-city', 'Nashik');

    const mayLeave = unsavedChangesGuard(
      fixture.componentInstance,
      null as unknown as ActivatedRouteSnapshot,
      null as unknown as RouterStateSnapshot,
      null as unknown as RouterStateSnapshot,
    );

    expect(confirmed).toHaveBeenCalled();
    expect(mayLeave).toBe(false);
  });

  it('lets an untouched form be left without a prompt', () => {
    arrive();
    const confirmed = vi.spyOn(window, 'confirm').mockReturnValue(false);

    const mayLeave = unsavedChangesGuard(
      fixture.componentInstance,
      null as unknown as ActivatedRouteSnapshot,
      null as unknown as RouterStateSnapshot,
      null as unknown as RouterStateSnapshot,
    );

    expect(confirmed).not.toHaveBeenCalled();
    expect(mayLeave).toBe(true);
  });
});
