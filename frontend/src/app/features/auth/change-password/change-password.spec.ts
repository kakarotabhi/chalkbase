import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { SessionStore } from '../../../core/auth/session-store';
import { ChangePassword } from './change-password';

describe('ChangePassword', () => {
  let fixture: ComponentFixture<ChangePassword>;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';
  const field = (id: string) => element().querySelector(`#${id}`) as HTMLInputElement;
  const submitButton = () => element().querySelector('cb-button button') as HTMLButtonElement;

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

  const arrive = (signedIn = true) => {
    if (signedIn) {
      TestBed.inject(SessionStore).signedIn(
        {
          userId: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
          displayName: 'Priya Sharma',
          mustChangePassword: true,
          school: { code: 'GPS-S12', name: 'Greenfield Public School' },
        },
        'temp-issued-one',
      );
    }
    fixture = TestBed.createComponent(ChangePassword);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChangePassword],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  it('shows every rule as unmet before anything is typed', () => {
    arrive();

    expect(text()).toContain('At least 10 characters');
    expect(text()).toContain('One number');
    expect(text()).toContain('One symbol');
    expect(element().querySelectorAll('.rules__item.is-met').length).toBe(0);
  });

  it('ticks each rule off as it is met, live', () => {
    arrive();

    type('new-password', 'chalkboard');
    expect(element().querySelectorAll('.rules__item.is-met').length).toBe(1);

    type('new-password', 'chalkboard9');
    expect(element().querySelectorAll('.rules__item.is-met').length).toBe(2);

    type('new-password', 'chalkboard9!');
    expect(element().querySelectorAll('.rules__item.is-met').length).toBe(3);
  });

  it('lets the field be read out with its rules attached', () => {
    arrive();

    expect(field('new-password').getAttribute('aria-describedby')).toContain('new-password-rules');
    expect(element().querySelector('#new-password-rules')).not.toBeNull();
  });

  it('will not submit two passwords that differ', () => {
    arrive();

    type('new-password', 'chalkboard9!');
    type('confirm-password', 'chalkboard9');
    submit();

    expect(text()).toContain('Both passwords must match.');
    httpMock.expectNone('/api/auth/password');
  });

  it('sends the temporary password as the current one and continues into the app', () => {
    arrive();

    type('new-password', 'chalkboard9!');
    type('confirm-password', 'chalkboard9!');
    submit();

    const request = httpMock.expectOne('/api/auth/password');
    expect(request.request.body).toEqual({
      currentPassword: 'temp-issued-one',
      newPassword: 'chalkboard9!',
    });
    expect(submitButton().getAttribute('aria-busy')).toBe('true');

    request.flush({ success: true, timestamp: '2026-09-05T10:00:00Z' });
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith('/');
    expect(TestBed.inject(SessionStore).mustChangePassword()).toBe(false);
  });

  const failWith = (code: string) => {
    arrive();

    type('new-password', 'chalkboard9!');
    type('confirm-password', 'chalkboard9!');
    submit();

    httpMock.expectOne('/api/auth/password').flush(
      {
        success: false,
        timestamp: '2026-09-05T10:00:00Z',
        error: { code, message: 'Rejected.' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
  };

  it('keeps the user on the form and says so when the change is refused', () => {
    failWith('AUTH_999');

    expect(text()).toContain('Could not set your password');
    expect(submitButton().disabled).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('points a rejected password back at the rules (AUTH_007)', () => {
    failWith('AUTH_007');

    expect(text()).toContain('That password does not meet the rules');
  });

  it('tells the user to sign in again when the temporary password is stale (AUTH_006)', () => {
    failWith('AUTH_006');

    expect(text()).toContain('That temporary password is no longer valid');
  });

  it('sends anyone who arrives without a session back to sign in', () => {
    arrive(false);

    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
