import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { apiErrorCode } from '../../../core/api/api-error';
import { AUTH_ERROR, AuthApi } from '../../../core/api/auth-api';
import { SessionStore } from '../../../core/auth/session-store';
import { Button } from '../../../shared/components/button/button';
import { FormField } from '../../../shared/components/form-field/form-field';
import { PasswordInput } from '../../../shared/components/password-input/password-input';
import { AuthLayout } from '../auth-layout/auth-layout';

/** The rules a new password must meet, in the order they are listed on screen. */
const PASSWORD_RULES = [
  { label: 'At least 10 characters', met: (value: string) => value.length >= 10 },
  { label: 'One number', met: (value: string) => /[0-9]/.test(value) },
  { label: 'One symbol', met: (value: string) => /[^A-Za-z0-9]/.test(value) },
] as const;

const RULES_ID = 'new-password-rules';

function meetsPasswordRules(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '');
  return PASSWORD_RULES.every((rule) => rule.met(value)) ? null : { passwordRules: true };
}

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const newPassword = group.get('newPassword')?.value;
  const confirmPassword = group.get('confirmPassword')?.value;
  return newPassword === confirmPassword ? null : { passwordMismatch: true };
}

/**
 * The forced password change after a first sign-in with a school-issued temporary password.
 *
 * The rules validate live rather than failing on submit: someone typing a password they will have
 * to remember deserves to see it become acceptable, not to be told afterwards that it was not.
 */
@Component({
  selector: 'cb-change-password',
  imports: [ReactiveFormsModule, AuthLayout, FormField, PasswordInput, Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './change-password.html',
  styleUrl: './change-password.scss',
})
export class ChangePassword implements OnInit {
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly rulesId = RULES_ID;

  protected readonly form = this.formBuilder.group(
    {
      newPassword: ['', [Validators.required, meetsPasswordRules]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  protected readonly submitting = signal(false);
  /** The `error.code` of the last failed attempt, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  private readonly attempted = signal(false);

  protected readonly failure = computed(() => {
    switch (this.failureCode()) {
      case null:
        return null;
      case AUTH_ERROR.CURRENT_PASSWORD_WRONG:
        return {
          title: 'That temporary password is no longer valid',
          detail: 'Sign in again with the password your school issued you.',
        };
      case AUTH_ERROR.WEAK_PASSWORD:
        return {
          title: 'That password does not meet the rules',
          detail: 'Check the three rules above and choose another.',
        };
      default:
        return {
          title: 'Could not set your password',
          detail: 'Try again, or ask your school office for a new temporary password.',
        };
    }
  });

  private readonly formEvents = toSignal(this.form.events, { initialValue: null });

  private readonly newPassword = toSignal(this.form.controls.newPassword.valueChanges, {
    initialValue: '',
  });

  protected readonly rules = computed(() => {
    const value = this.newPassword();
    return PASSWORD_RULES.map((rule) => ({ label: rule.label, met: rule.met(value) }));
  });

  protected readonly newPasswordError = computed(() => {
    this.formEvents();
    const control = this.form.controls.newPassword;
    if (control.valid || !(control.touched || this.attempted())) {
      return null;
    }
    // The checklist below the field already says which rule is unmet, so this only has to cover
    // the empty case — repeating the rules here would be two answers to one question.
    return control.hasError('required') ? 'Enter a new password.' : 'This password is too weak.';
  });

  protected readonly confirmPasswordError = computed(() => {
    this.formEvents();
    const control = this.form.controls.confirmPassword;
    if (!(control.touched || this.attempted())) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Type the new password again.';
    }
    return this.form.hasError('passwordMismatch') ? 'Both passwords must match.' : null;
  });

  ngOnInit(): void {
    // Nothing here works without the temporary password, and it is held in memory only — a reload
    // loses it. Sending the user back to sign in beats a form that can only fail.
    if (this.session.temporaryPassword() === null) {
      void this.router.navigateByUrl('/login');
    }
  }

  protected submit(): void {
    if (this.submitting()) {
      return;
    }

    this.attempted.set(true);
    this.form.markAllAsTouched();

    if (this.form.invalid) {
      this.focusFirstInvalid();
      return;
    }

    const currentPassword = this.session.temporaryPassword();
    if (currentPassword === null) {
      void this.router.navigateByUrl('/login');
      return;
    }

    this.failureCode.set(null);
    this.submitting.set(true);

    this.authApi
      .changePassword({ currentPassword, newPassword: this.form.getRawValue().newPassword })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.session.passwordChanged();
          void this.router.navigateByUrl('/');
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  private focusFirstInvalid(): void {
    const id = this.form.controls.newPassword.invalid ? 'new-password' : 'confirm-password';
    this.host.nativeElement.querySelector<HTMLElement>(`#${id}`)?.focus();
  }
}
