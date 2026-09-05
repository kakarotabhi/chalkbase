import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { apiErrorCode } from '../../../core/api/api-error';
import { AUTH_ERROR, AuthApi } from '../../../core/api/auth-api';
import { DevicePreferences } from '../../../core/auth/device-preferences';
import { safeReturnTo } from '../../../core/auth/return-to';
import { SessionStore } from '../../../core/auth/session-store';
import { Button } from '../../../shared/components/button/button';
import { Checkbox } from '../../../shared/components/checkbox/checkbox';
import { FormField } from '../../../shared/components/form-field/form-field';
import { PasswordInput } from '../../../shared/components/password-input/password-input';
import { TextInput } from '../../../shared/components/text-input/text-input';
import { AuthLayout } from '../auth-layout/auth-layout';

interface SignInBanner {
  readonly tone: 'danger';
  readonly icon: 'alert' | 'lock';
  readonly title: string;
  readonly detail: string;
}

/** Ids are fixed rather than generated so the label, the error and the focus target all agree. */
const FIELD_IDS = {
  schoolCode: 'login-school-code',
  username: 'login-username',
  password: 'login-password',
} as const;

/**
 * Sign in. The one screen with no navigation — there is nowhere to go until you are through it,
 * so it does not render inside the app shell.
 */
@Component({
  selector: 'cb-login',
  imports: [ReactiveFormsModule, AuthLayout, FormField, TextInput, PasswordInput, Checkbox, Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(SessionStore);
  private readonly device = inject(DevicePreferences);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly fieldIds = FIELD_IDS;

  protected readonly form = this.formBuilder.group({
    // Pre-filled from the last successful sign-in on this device, so a parent types their school
    // code once and never again.
    schoolCode: [this.device.schoolCode() ?? '', Validators.required],
    username: ['', Validators.required],
    password: ['', Validators.required],
    keepSignedIn: [this.device.keepSignedIn()],
  });

  protected readonly submitting = signal(false);
  /** The `error.code` of the last failed attempt, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  private readonly attempted = signal(false);
  protected readonly recoveryExplained = signal(false);

  // Reactive forms are not signals, so nothing below would recompute on touch or status changes
  // without something to depend on. `form.events` is that something.
  private readonly formEvents = toSignal(this.form.events, { initialValue: null });

  protected readonly isLocked = computed(() => this.failureCode() === AUTH_ERROR.ACCOUNT_LOCKED);

  protected readonly banner = computed<SignInBanner | null>(() => {
    switch (this.failureCode()) {
      case null:
        return null;
      case AUTH_ERROR.INVALID_CREDENTIALS:
        return {
          tone: 'danger',
          icon: 'alert',
          // Deliberately the same wording for a wrong password and an unknown user. Saying which
          // one was wrong tells anyone who asks whose parents are registered here.
          title: 'Invalid username or password',
          detail: 'Check both and try again.',
        };
      case AUTH_ERROR.ACCOUNT_LOCKED:
        return {
          tone: 'danger',
          icon: 'lock',
          title: 'Account locked',
          detail:
            'Too many attempts. Try again in 15 minutes, or ask your school office to unlock it.',
        };
      case AUTH_ERROR.UNKNOWN_SCHOOL:
        return {
          tone: 'danger',
          icon: 'alert',
          title: 'We do not recognise that school code',
          detail: 'Check the code your school gave you and try again.',
        };
      default:
        return {
          tone: 'danger',
          icon: 'alert',
          title: 'Could not sign you in',
          detail: 'Chalkbase could not be reached. Check your connection and try again.',
        };
    }
  });

  protected readonly schoolCodeError = computed(() =>
    this.failureCode() === AUTH_ERROR.UNKNOWN_SCHOOL
      ? 'No school has this code.'
      : this.requiredError('schoolCode', 'Enter your school code.'),
  );

  protected readonly usernameError = computed(() =>
    this.requiredError('username', 'Enter your username or email.'),
  );

  protected readonly passwordError = computed(() =>
    this.requiredError('password', 'Enter your password.'),
  );

  protected submit(): void {
    if (this.submitting() || this.isLocked()) {
      return;
    }

    this.attempted.set(true);
    this.form.markAllAsTouched();

    if (this.form.invalid) {
      this.focusFirstInvalid();
      return;
    }

    const { schoolCode, username, password, keepSignedIn } = this.form.getRawValue();

    this.failureCode.set(null);
    this.submitting.set(true);

    this.authApi
      .login({
        schoolCode: schoolCode.trim().toUpperCase(),
        username: username.trim(),
        password,
        rememberMe: keepSignedIn,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (user) => {
          this.submitting.set(false);
          this.device.rememberSchoolCode(schoolCode);
          this.device.setKeepSignedIn(keepSignedIn);
          this.session.signedIn(user, password);
          void this.router.navigateByUrl(this.destination(user.mustChangePassword));
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  /**
   * Where sign-in lands.
   *
   * A temporary password wins over everything: there is nothing useful to do with an account that
   * still has the password printed on the slip the office handed over. Otherwise the guard's
   * `returnTo` is honoured, so following a deep link and being asked to sign in first ends where
   * the link pointed.
   *
   * `returnTo` arrives from the address bar, which means it is attacker-supplied — `safeReturnTo`
   * is what stops this being an open redirect, and dropping it is not optional.
   */
  private destination(mustChangePassword: boolean): string {
    if (mustChangePassword) {
      return '/change-password';
    }
    return safeReturnTo(this.route.snapshot.queryParamMap.get('returnTo')) ?? '/';
  }

  /**
   * There is no self-service reset: passwords are issued by the school office, so the honest
   * answer is the help note at the bottom of the form. This moves focus to it rather than sending
   * anyone to a page that cannot help them.
   */
  protected explainRecovery(): void {
    this.recoveryExplained.set(true);
    this.host.nativeElement.querySelector<HTMLElement>('#login-help')?.focus();
  }

  private requiredError(
    name: 'schoolCode' | 'username' | 'password',
    message: string,
  ): string | null {
    // Read so this recomputes whenever the form reports a value, status or touched change.
    this.formEvents();

    const control = this.form.controls[name];
    if (control.valid || !(control.touched || this.attempted())) {
      return null;
    }
    return message;
  }

  private focusFirstInvalid(): void {
    const order = ['schoolCode', 'username', 'password'] as const;
    const first = order.find((name) => this.form.controls[name].invalid);
    if (first) {
      this.host.nativeElement.querySelector<HTMLElement>(`#${FIELD_IDS[first]}`)?.focus();
    }
  }
}
