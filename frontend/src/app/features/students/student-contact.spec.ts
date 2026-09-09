import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ContactDetail } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { StudentContact } from './student-contact';

const STUDENT = '018f3a10-0000-7000-8000-000000000001';
const CONTACT_URL = `/api/students/${STUDENT}/contact`;

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-09T10:00:00Z',
  traceId: 'test-trace',
  data,
});

/**
 * The screen the reported defect was found on: typing `not-an-email` into the email field and
 * pressing Save did nothing — no message, no `aria-invalid`, nothing announced. These specs are
 * what proves that no longer happens, and the first specs any of the seven student record cards
 * have had at all.
 */
describe('StudentContact', () => {
  let fixture: ComponentFixture<StudentContact>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const button = (label: string) =>
    Array.from(element().querySelectorAll('button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement | undefined;

  const type = (id: string, value: string) => {
    const input = element().querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const submit = () => {
    (element().querySelector('form.editor') as HTMLFormElement).dispatchEvent(
      new Event('submit', { cancelable: true }),
    );
    fixture.detectChanges();
  };

  const arrive = (contact: ContactDetail | null = null) => {
    fixture = TestBed.createComponent(StudentContact);
    fixture.componentRef.setInput('studentId', STUDENT);
    fixture.componentRef.setInput('contact', contact);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudentContact],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    signInWith(Permissions.STUDENT_READ, Permissions.STUDENT_MANAGE);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows the recorded contact details', () => {
    arrive({ address: '12 MG Road', phone: '9000000001', email: 'parent@example.com' });

    expect(text()).toContain('12 MG Road');
    expect(text()).toContain('9000000001');
    expect(text()).toContain('parent@example.com');
  });

  it('says nothing is recorded when the record is empty', () => {
    arrive();

    expect(text()).toContain('Not recorded');
  });

  // ── The reported defect ──────────────────────────────────────────────────────────────────

  /**
   * `not-an-email` into the email field, then Save: this is the exact repro. Before `[error]` and
   * `[invalid]` were wired up, `cb-form-field` never rendered a message and the input carried no
   * `aria-invalid`, so a screen-reader user got no explanation for why nothing happened.
   */
  it('says why an invalid email refuses to save, and marks the field for assistive technology', () => {
    arrive();
    button('Edit')!.click();
    fixture.detectChanges();

    const email = element().querySelector('#contact-email') as HTMLInputElement;
    expect(email.getAttribute('aria-invalid')).toBeNull();

    type('contact-email', 'not-an-email');
    submit();

    expect(text()).toContain('Enter an email address like name@example.com');
    expect(email.getAttribute('aria-invalid')).toBe('true');
    // Nothing was sent: `httpMock.verify()` in `afterEach` is what asserts that.
  });

  it('says nothing about the email until the form has actually been touched or submitted', () => {
    arrive();
    button('Edit')!.click();
    fixture.detectChanges();

    expect(text()).not.toContain('Enter an email address like name@example.com');
  });

  it('clears the email message once the address is corrected', () => {
    arrive();
    button('Edit')!.click();
    fixture.detectChanges();

    type('contact-email', 'not-an-email');
    submit();
    expect(text()).toContain('Enter an email address like name@example.com');

    type('contact-email', 'parent@example.com');
    fixture.detectChanges();

    expect(text()).not.toContain('Enter an email address like name@example.com');
  });

  it('says a phone number over the limit is too long', () => {
    arrive();
    button('Edit')!.click();
    fixture.detectChanges();

    type('contact-phone', '9'.repeat(21));
    submit();

    expect(text()).toContain('A phone number is 20 characters or fewer.');
  });

  it('saves valid contact details and reports success', () => {
    arrive();
    button('Edit')!.click();
    fixture.detectChanges();

    type('contact-address', '12 MG Road');
    type('contact-phone', '9000000001');
    type('contact-email', 'parent@example.com');
    submit();

    const request = httpMock.expectOne({ url: CONTACT_URL, method: 'PUT' });
    expect(request.request.body).toEqual({
      address: '12 MG Road',
      phone: '9000000001',
      email: 'parent@example.com',
    });
    request.flush(
      envelope({ address: '12 MG Road', phone: '9000000001', email: 'parent@example.com' }),
    );
    fixture.detectChanges();

    // The editor closes and the "Edit" button comes back — `changed` is an output this component
    // emits for the parent to announce, so there is nothing of the announcement itself to read
    // off this component's own template.
    expect(element().querySelector('form.editor')).toBeNull();
    expect(button('Edit')).toBeDefined();
  });

  it('does not offer to edit without permission', () => {
    signInWith(Permissions.STUDENT_READ);
    arrive();

    expect(button('Edit')).toBeUndefined();
  });
});
