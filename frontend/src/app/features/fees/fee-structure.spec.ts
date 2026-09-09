import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AcademicSession, FeeHead, FeeStructure, SchoolClass } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { FeeStructurePage } from './fee-structure';

const SESSIONS_URL = '/api/academics/sessions';
const CLASSES_URL = '/api/academics/classes';
const HEADS_URL = '/api/fees/heads';
const STRUCTURES_URL = '/api/fees/structures';

const CURRENT_SESSION: AcademicSession = {
  id: '018f3a10-0000-7000-8000-00000000a001',
  name: '2026–27',
  startsOn: '2026-04-01',
  endsOn: '2027-03-31',
  current: true,
};

const CLASS_FIVE: SchoolClass = {
  id: '018f3a10-0000-7000-8000-00000000c001',
  name: 'Class 5',
  sequence: 5,
  active: true,
  sections: [],
};

const TUITION: FeeHead = {
  id: '018f3a10-0000-7000-8000-00000000f001',
  name: 'Tuition Fee',
  category: 'TUITION',
  active: true,
};

const STRUCTURE: FeeStructure = {
  id: '018f3a10-0000-7000-8000-00000000s001',
  academicSessionId: CURRENT_SESSION.id,
  academicSessionName: CURRENT_SESSION.name,
  schoolClassId: CLASS_FIVE.id,
  schoolClassName: CLASS_FIVE.name,
  version: 1,
  createdAt: '2026-04-01T00:00:00Z',
  items: [
    {
      id: '018f3a10-0000-7000-8000-00000000i001',
      feeHeadId: TUITION.id,
      feeHeadName: TUITION.name,
      feeHeadCategory: 'TUITION',
      amount: 12000,
      frequency: 'ANNUAL',
      installments: [
        { id: '018f3a10-0000-7000-8000-00000000n001', dueDate: '2026-04-10', amount: 12000 },
      ],
    },
  ],
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

describe('FeeStructurePage', () => {
  let fixture: ComponentFixture<FeeStructurePage>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const arrive = (structures: readonly FeeStructure[] = [STRUCTURE]) => {
    fixture = TestBed.createComponent(FeeStructurePage);
    fixture.detectChanges();
    httpMock.expectOne({ url: SESSIONS_URL, method: 'GET' }).flush(envelope([CURRENT_SESSION]));
    httpMock.expectOne({ url: CLASSES_URL, method: 'GET' }).flush(envelope([CLASS_FIVE]));
    httpMock.expectOne({ url: HEADS_URL, method: 'GET' }).flush(envelope([TUITION]));
    httpMock
      .expectOne(
        (req) => req.url === STRUCTURES_URL && req.params.get('sessionId') === CURRENT_SESSION.id,
      )
      .flush(envelope(structures));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FeeStructurePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    signInWith(
      Permissions.FEE_STRUCTURE_READ,
      Permissions.FEE_STRUCTURE_MANAGE,
      Permissions.FEE_HEAD_READ,
    );
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows a class with a structure as set up, and one without as not set up', () => {
    arrive([]);

    expect(text()).toContain('Class 5');
    expect(text()).toContain('Not set up');
  });

  it('shows the version and total once a structure exists', () => {
    arrive();

    expect(text()).toContain('v1');
    expect(text()).toContain('12,000');
  });

  it('opens the editor pre-filled from the current structure', () => {
    arrive();

    (element().querySelector('.cell--action cb-button button') as HTMLButtonElement)?.click();
    fixture.detectChanges();

    expect(text()).toContain("Class 5's fee structure");
    const amountInput = element().querySelector(
      '.item-card__row input[type="number"]',
    ) as HTMLInputElement;
    expect(amountInput.value).toBe('12000');
  });

  it('adds a fee head and a due date, then saves as a new version', () => {
    arrive([]);

    (element().querySelector('.cell--action cb-button button') as HTMLButtonElement)?.click();
    fixture.detectChanges();

    (
      Array.from(element().querySelectorAll('cb-button button')).find((button) =>
        (button.textContent ?? '').includes('Add a fee head'),
      ) as HTMLButtonElement
    )?.click();
    fixture.detectChanges();

    const amountInput = element().querySelector(
      '.item-card__row input[type="number"]',
    ) as HTMLInputElement;
    amountInput.value = '5000';
    amountInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (
      Array.from(element().querySelectorAll('cb-button button')).find((button) =>
        (button.textContent ?? '').includes('Add a due date'),
      ) as HTMLButtonElement
    )?.click();
    fixture.detectChanges();

    const dateInput = element().querySelector('input[type="date"]') as HTMLInputElement;
    dateInput.value = '2026-04-10';
    dateInput.dispatchEvent(new Event('input'));
    const installmentAmount = element().querySelectorAll(
      '.installment-row input[type="number"]',
    )[0] as HTMLInputElement;
    installmentAmount.value = '5000';
    installmentAmount.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const saveButton = Array.from(element().querySelectorAll('cb-button button')).find(
      (button) => button.textContent?.trim() === 'Save',
    ) as HTMLButtonElement;
    expect(saveButton.disabled).toBe(false);
    saveButton.click();

    const save = httpMock.expectOne({
      url: `${STRUCTURES_URL}/${CURRENT_SESSION.id}/${CLASS_FIVE.id}`,
      method: 'PUT',
    });
    expect(save.request.body.items[0].amount).toBe(5000);
    save.flush(envelope({ ...STRUCTURE, version: 1 }));
    httpMock
      .expectOne(
        (req) => req.url === STRUCTURES_URL && req.params.get('sessionId') === CURRENT_SESSION.id,
      )
      .flush(envelope([STRUCTURE]));
    fixture.detectChanges();

    expect(text()).toContain('saved as version 1');
  });

  it('explains the lock rule by name when the session has already run', () => {
    arrive([]);

    (element().querySelector('.cell--action cb-button button') as HTMLButtonElement)?.click();
    fixture.detectChanges();
    (
      Array.from(element().querySelectorAll('cb-button button')).find((button) =>
        (button.textContent ?? '').includes('Add a fee head'),
      ) as HTMLButtonElement
    )?.click();
    fixture.detectChanges();
    const amountInput = element().querySelector(
      '.item-card__row input[type="number"]',
    ) as HTMLInputElement;
    amountInput.value = '5000';
    amountInput.dispatchEvent(new Event('input'));
    (
      Array.from(element().querySelectorAll('cb-button button')).find((button) =>
        (button.textContent ?? '').includes('Add a due date'),
      ) as HTMLButtonElement
    )?.click();
    fixture.detectChanges();
    const dateInput = element().querySelector('input[type="date"]') as HTMLInputElement;
    dateInput.value = '2026-04-10';
    dateInput.dispatchEvent(new Event('input'));
    const installmentAmount = element().querySelectorAll(
      '.installment-row input[type="number"]',
    )[0] as HTMLInputElement;
    installmentAmount.value = '5000';
    installmentAmount.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (
      Array.from(element().querySelectorAll('cb-button button')).find(
        (button) => button.textContent?.trim() === 'Save',
      ) as HTMLButtonElement
    )?.click();

    httpMock
      .expectOne({ url: `${STRUCTURES_URL}/${CURRENT_SESSION.id}/${CLASS_FIVE.id}`, method: 'PUT' })
      .flush(refusal('FEE_009'), { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(text()).toContain('This academic session has already run');
  });
});
