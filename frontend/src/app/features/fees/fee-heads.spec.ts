import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeeConcessionType, FeeHead } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { FeeHeads } from './fee-heads';

const HEADS_URL = '/api/fees/heads';
const CONCESSION_TYPES_URL = '/api/fees/concession-types';

const TUITION: FeeHead = {
  id: '018f3a10-0000-7000-8000-00000000f001',
  name: 'Tuition Fee',
  category: 'TUITION',
  active: true,
};

const DEVELOPMENT: FeeHead = {
  id: '018f3a10-0000-7000-8000-00000000f002',
  name: 'Development Fee',
  category: 'ANNUAL_DEVELOPMENT',
  capPercentOfTuition: 15,
  active: true,
};

const SIBLING: FeeConcessionType = {
  id: '018f3a10-0000-7000-8000-00000000c001',
  name: 'Sibling discount',
  category: 'SIBLING',
  requiresApproval: true,
  active: true,
};

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

describe('FeeHeads', () => {
  let fixture: ComponentFixture<FeeHeads>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const headsRequest = () => httpMock.expectOne({ url: HEADS_URL, method: 'GET' });
  const concessionsRequest = () => httpMock.expectOne({ url: CONCESSION_TYPES_URL, method: 'GET' });

  const arrive = (
    heads: readonly FeeHead[] = [TUITION, DEVELOPMENT],
    concessionTypes: readonly FeeConcessionType[] = [SIBLING],
  ) => {
    fixture = TestBed.createComponent(FeeHeads);
    fixture.detectChanges();
    headsRequest().flush(envelope(heads));
    concessionsRequest().flush(envelope(concessionTypes));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FeeHeads],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    signInWith(
      Permissions.FEE_HEAD_READ,
      Permissions.FEE_HEAD_MANAGE,
      Permissions.FEE_CONCESSION_TYPE_READ,
      Permissions.FEE_CONCESSION_TYPE_MANAGE,
    );
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists fee heads with their category and any cap', () => {
    arrive();

    expect(text()).toContain('Tuition Fee');
    expect(text()).toContain('Development Fee');
    expect(text()).toContain('15');
  });

  it('lists concession types, flagging one that requires approval', () => {
    arrive();

    expect(text()).toContain('Sibling discount');
    expect(text()).toContain('Needs approval');
  });

  /**
   * The two lists are gated on two different permissions (`RoleTemplates`'s own Javadoc has the
   * argument for why) — a caller holding only one still sees that section.
   */
  it('shows each section on its own permission, independently of the other', () => {
    signInWith(Permissions.FEE_HEAD_READ, Permissions.FEE_HEAD_MANAGE);

    fixture = TestBed.createComponent(FeeHeads);
    fixture.detectChanges();
    headsRequest().flush(envelope([TUITION]));
    concessionsRequest().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('Tuition Fee');
    expect(text()).toContain('You do not have permission to view concession types');
  });

  it('adds a fee head and shows what was added', () => {
    arrive([]);

    (element().querySelector('#head-add') as HTMLButtonElement | null)?.click();
    fixture.detectChanges();

    const name = element().querySelector('#head-name') as HTMLInputElement;
    name.value = 'Admission Fee';
    name.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (element().querySelector('form.editor') as HTMLFormElement).dispatchEvent(
      new Event('submit', { cancelable: true }),
    );

    const create = httpMock.expectOne({ url: HEADS_URL, method: 'POST' });
    expect(create.request.body.name).toBe('Admission Fee');
    expect(create.request.body.category).toBe('TUITION');
    expect(create.request.body.active).toBe(true);
    expect(create.request.body.capPercentOfTuition).toBeUndefined();
    create.flush(
      envelope({
        id: '018f3a10-0000-7000-8000-00000000f003',
        name: 'Admission Fee',
        category: 'TUITION',
        active: true,
      }),
    );
    headsRequest().flush(
      envelope([
        {
          id: '018f3a10-0000-7000-8000-00000000f003',
          name: 'Admission Fee',
          category: 'TUITION',
          active: true,
        },
      ]),
    );
    fixture.detectChanges();

    expect(text()).toContain('Admission Fee added.');
  });

  // ── The reported defect: a form that refuses to save without saying why ─────────────────

  /**
   * `cb-form-field` has always supported an `[error]` input; `attempted` and `serverErrors` were
   * already tracked here and set on every refused save — nothing ever read them back into a
   * message. This is the fix, and the DOM assertion at the end is what the reported defect looked
   * like on screen: the button did nothing, silently.
   */
  it('says a fee head needs a name, and refuses to save one without one', () => {
    arrive([]);

    (element().querySelector('#head-add') as HTMLButtonElement | null)?.click();
    fixture.detectChanges();

    const name = element().querySelector('#head-name') as HTMLInputElement;
    expect(name.getAttribute('aria-invalid')).toBeNull();

    (element().querySelector('form.editor') as HTMLFormElement).dispatchEvent(
      new Event('submit', { cancelable: true }),
    );
    fixture.detectChanges();

    expect(text()).toContain('Give this fee head a name.');
    expect(name.getAttribute('aria-invalid')).toBe('true');
    // Nothing was sent: `httpMock.verify()` in `afterEach` is what asserts that.
  });

  it('clears the fee head name message once a name is typed', () => {
    arrive([]);

    (element().querySelector('#head-add') as HTMLButtonElement | null)?.click();
    fixture.detectChanges();
    (element().querySelector('form.editor') as HTMLFormElement).dispatchEvent(
      new Event('submit', { cancelable: true }),
    );
    fixture.detectChanges();
    expect(text()).toContain('Give this fee head a name.');

    const name = element().querySelector('#head-name') as HTMLInputElement;
    name.value = 'Admission Fee';
    name.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(text()).not.toContain('Give this fee head a name.');
  });

  it('says a concession type needs a name, and refuses to save one without one', () => {
    arrive([], []);

    (element().querySelector('#concession-add') as HTMLButtonElement | null)?.click();
    fixture.detectChanges();

    (element().querySelector('form.editor') as HTMLFormElement).dispatchEvent(
      new Event('submit', { cancelable: true }),
    );
    fixture.detectChanges();

    expect(text()).toContain('Give this concession type a name.');
    const name = element().querySelector('#concession-name') as HTMLInputElement;
    expect(name.getAttribute('aria-invalid')).toBe('true');
  });
});
