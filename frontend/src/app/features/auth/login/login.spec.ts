import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AUTH_ERROR } from '../../../core/api/auth-api';
import { SessionStore } from '../../../core/auth/session-store';
import { Login } from './login';

const SUCCESS = {
  success: true,
  timestamp: '2026-09-05T10:00:00Z',
  data: {
    userId: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
    displayName: 'Priya Sharma',
    mustChangePassword: false,
    school: { code: 'GPS-S12', name: 'Greenfield Public School' },
    permissions: [],
  },
};

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
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

  const fillCredentials = () => {
    type('login-school-code', 'GPS-S12');
    type('login-username', 'priya.sharma');
    type('login-password', 'secret-one');
  };

  const signIn = () => {
    fillCredentials();
    element().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    try {
      localStorage.clear();
    } catch {
      // Nothing to clear if storage is unavailable; the component copes either way.
    }

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    httpMock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  it('starts empty, with a usable button and nothing to apologise for', () => {
    expect(text()).toContain('Sign in');
    expect(field('login-school-code').value).toBe('');
    expect(field('login-username').value).toBe('');
    expect(field('login-password').value).toBe('');
    expect(submitButton().disabled).toBe(false);
    expect(element().querySelector('.banner')).toBeNull();
  });

  it('asks for the fields it needs instead of sending an empty form', () => {
    element().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(text()).toContain('Enter your school code.');
    expect(text()).toContain('Enter your username or email.');
    expect(text()).toContain('Enter your password.');
    httpMock.expectNone('/api/auth/login');
  });

  it('signs in and sends the user on to the app', () => {
    signIn();

    const request = httpMock.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({
      schoolCode: 'GPS-S12',
      username: 'priya.sharma',
      password: 'secret-one',
      rememberMe: false,
    });

    request.flush(SUCCESS);
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith('/');
    expect(TestBed.inject(SessionStore).user()?.displayName).toBe('Priya Sharma');
  });

  it('shows progress on the button and freezes the fields while signing in', () => {
    signIn();

    const request = httpMock.expectOne('/api/auth/login');

    expect(submitButton().textContent).toContain('Signing in…');
    expect(submitButton().disabled).toBe(true);
    expect(submitButton().getAttribute('aria-busy')).toBe('true');
    expect(field('login-username').readOnly).toBe(true);
    expect(field('login-password').readOnly).toBe(true);
    // The fields are read-only, not removed or disabled — nothing on the form moves.
    expect(field('login-username').value).toBe('priya.sharma');

    request.flush(SUCCESS);
    fixture.detectChanges();
  });

  it('says the same thing for a wrong password as for an unknown user (AUTH_001)', () => {
    signIn();

    httpMock.expectOne('/api/auth/login').flush(
      {
        success: false,
        timestamp: '2026-09-05T10:00:00Z',
        error: { code: AUTH_ERROR.INVALID_CREDENTIALS, message: 'Nope.' },
      },
      { status: 401, statusText: 'Unauthorized' },
    );
    fixture.detectChanges();

    expect(text()).toContain('Invalid username or password');
    expect(text()).toContain('Check both and try again.');
    // Nothing narrows it down to one field, and the user can try again.
    expect(submitButton().disabled).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('explains a locked account and disables the button without hiding it (AUTH_003)', () => {
    signIn();

    httpMock.expectOne('/api/auth/login').flush(
      {
        success: false,
        timestamp: '2026-09-05T10:00:00Z',
        error: { code: AUTH_ERROR.ACCOUNT_LOCKED, message: 'Locked.' },
      },
      { status: 401, statusText: 'Unauthorized' },
    );
    fixture.detectChanges();

    expect(text()).toContain('Account locked');
    expect(text()).toContain('ask your school office to unlock it');
    expect(submitButton()).not.toBeNull();
    expect(submitButton().disabled).toBe(true);

    // A second submit while locked must not reach the network.
    element().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    httpMock.expectNone('/api/auth/login');
  });

  it('points an unknown school code at the field that is wrong (AUTH_005)', () => {
    signIn();

    httpMock.expectOne('/api/auth/login').flush(
      {
        success: false,
        timestamp: '2026-09-05T10:00:00Z',
        error: { code: AUTH_ERROR.UNKNOWN_SCHOOL, message: 'No such school.' },
      },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    expect(text()).toContain('We do not recognise that school code');
    expect(text()).toContain('No school has this code.');
    expect(field('login-school-code').getAttribute('aria-invalid')).toBe('true');
  });

  it('falls back to a general message when the request never lands', () => {
    signIn();

    httpMock
      .expectOne('/api/auth/login')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not sign you in');
    expect(text()).toContain('Check your connection and try again.');
  });

  it('sends a first-time user to set a password instead of into the app', () => {
    signIn();

    httpMock.expectOne('/api/auth/login').flush({
      ...SUCCESS,
      data: { ...SUCCESS.data, mustChangePassword: true },
    });
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith('/change-password');
  });

  it('remembers the school code for next time on this device', () => {
    signIn();
    httpMock.expectOne('/api/auth/login').flush(SUCCESS);
    fixture.detectChanges();

    expect(localStorage.getItem('cb.auth.schoolCode')).toBe('GPS-S12');
  });

  it('answers "forgot password?" with the only thing that actually helps', () => {
    const link = [...element().querySelectorAll('button')].find((button) =>
      button.textContent?.includes('Forgot password?'),
    ) as HTMLButtonElement;

    link.click();
    fixture.detectChanges();

    const help = element().querySelector('#login-help') as HTMLElement;
    expect(help.textContent).toContain('Your school office can reset your password.');
    expect(document.activeElement).toBe(help);
  });
});

describe('Login on a device that has signed in before', () => {
  beforeEach(async () => {
    localStorage.setItem('cb.auth.schoolCode', 'GPS-S12');

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => localStorage.clear());

  it('pre-fills the school code so a parent types it once', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();

    const schoolCode = fixture.nativeElement.querySelector(
      '#login-school-code',
    ) as HTMLInputElement;
    expect(schoolCode.value).toBe('GPS-S12');
  });
});

describe('Login arriving with a returnTo', () => {
  let fixture: ComponentFixture<Login>;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  const arriveFrom = async (returnTo: string) => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ returnTo }) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    httpMock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const type = (id: string, value: string) => {
      const input = element.querySelector(`#${id}`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    type('login-school-code', 'GPS-S12');
    type('login-username', 'priya.sharma');
    type('login-password', 'secret-one');

    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    httpMock.expectOne('/api/auth/login').flush(SUCCESS);
    fixture.detectChanges();
  };

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('takes the user on to the page they were trying to reach', async () => {
    await arriveFrom('/fees/receipts?year=2026');

    expect(navigate).toHaveBeenCalledWith('/fees/receipts?year=2026');
  });

  it('ignores an absolute URL to another origin', async () => {
    // An open redirect on a login form is a phishing kit: the victim signs in on the real site and
    // the real site hands them to the fake one.
    await arriveFrom('https://evil.example.com');

    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('ignores a protocol-relative URL', async () => {
    await arriveFrom('//evil.example.com');

    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('does not follow returnTo past a temporary password', async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ returnTo: '/schools' }) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    httpMock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    for (const [id, value] of [
      ['login-school-code', 'GPS-S12'],
      ['login-username', 'priya.sharma'],
      ['login-password', 'secret-one'],
    ]) {
      const input = element.querySelector(`#${id}`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();
    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    httpMock.expectOne('/api/auth/login').flush({
      ...SUCCESS,
      data: { ...SUCCESS.data, mustChangePassword: true },
    });
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith('/change-password');
  });
});
