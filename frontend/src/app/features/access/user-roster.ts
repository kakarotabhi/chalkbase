import { A11yModule } from '@angular/cdk/a11y';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import { IdentityAccessApi } from '../../core/api/identity-access-api';
import { UserSummary } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { SessionStore, permitted } from '../../core/auth/session-store';
import { Badge } from '../../shared/components/badge/badge';
import { Button } from '../../shared/components/button/button';
import { Dialog } from '../../shared/components/dialog/dialog';
import { FormField } from '../../shared/components/form-field/form-field';
import { TextInput } from '../../shared/components/text-input/text-input';
import {
  ACCESS_DENIED,
  LAST_ACCESS_MANAGER,
  STATUS_LABELS,
  USERNAME_TAKEN,
  labelFor,
} from './access-shared';

/** `user_identifier.value` (username) is `varchar(100)`. */
const USERNAME_MAX_LENGTH = 100;
/** `user_account.display_name` is `varchar(200)`. */
const DISPLAY_NAME_MAX_LENGTH = 200;

/** What just happened to one account, shown once and never again — see `PasswordReveal` below. */
interface PasswordReveal {
  readonly displayName: string;
  /** Absent for a reset: `TemporaryPasswordResponse` does not carry it back, only the id does. */
  readonly username: string | null;
  readonly temporaryPassword: string;
  /** Whether the account acted on is the one currently signed in — see `acknowledgeReveal`. */
  readonly isSelf: boolean;
}

/** One row, with everything the template needs already decided. */
interface AccountRow {
  readonly id: string;
  readonly displayName: string;
  readonly status: string;
  readonly statusLabel: string;
  readonly active: boolean;
  readonly isSelf: boolean;
  readonly deactivateButtonId: string;
  readonly reactivateButtonId: string;
  readonly unlockButtonId: string;
  readonly resetButtonId: string;
}

/**
 * This school's account roster (`docs/status.md`: "Roles and permissions … User management").
 *
 * ## What this screen cannot show, and why
 *
 * `GET /api/access/users` answers `UserSummary` for every account in one call — id, display name,
 * status — and nothing else. There is no lockout flag and no last-login time on that list: only
 * the four per-account write endpoints answer `UserAccountResponse`, which does carry
 * `lockedUntil`. Fetching each account individually to paint a lock badge would be the N+1 request
 * pattern every list screen in this app avoids, so **Clear lockout** is offered on every active
 * account rather than only the ones known to need it, and the confirmation after clicking it is
 * where the truth comes from. This is a known gap (`docs/status.md`), not an oversight.
 *
 * ## A temporary password is shown exactly once
 *
 * Creating an account and resetting a password both answer with a plaintext password that nothing
 * on the backend stores and no endpoint can retrieve again. `passwordReveal` is the one state on
 * this screen a click outside or the Escape key cannot dismiss — see the template — because losing
 * it before it is copied means doing the whole thing again.
 *
 * ## Acting on your own account (ADR-0023)
 *
 * Deactivating or resetting an account ends every session it holds, immediately. Doing either to
 * the account that is doing it ends *this* session too, mid-screen. Both confirmations say so
 * before the request is sent, and `acknowledgeReveal`/`confirmDeactivate` sign this tab out and
 * send it to `/login` the moment the server has confirmed the change, rather than leaving it to
 * the next unrelated request to discover the session is gone.
 *
 * ## `AUTH_010` is a guard, not a failure
 *
 * Refusing to deactivate the last account that can manage access is the system protecting the
 * school, not refusing the admin something they are entitled to — see `statusFailure`.
 *
 * ## There is no guard on this route, deliberately
 *
 * ADR-0008: the server leaves the menu item out for anyone without `identity:user:read`, and the
 * endpoint enforces it independently. Typing the URL without it lands here and gets a 403 this
 * screen explains, exactly as the audit log does.
 */
@Component({
  selector: 'cb-user-roster',
  imports: [
    A11yModule,
    ReactiveFormsModule,
    RouterLink,
    Badge,
    Button,
    Dialog,
    FormField,
    TextInput,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './user-roster.html',
  styleUrl: './user-roster.scss',
})
export class UserRoster {
  private readonly api = inject(IdentityAccessApi);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  /** `identity:user:manage` — create, deactivate, reactivate, unlock, reset. */
  protected readonly canManageUsers = permitted(Permissions.USER_MANAGE);
  /** Whether to offer the "Manage roles" link per row — a different permission, `identity:role:manage`. */
  protected readonly canManageRoles = permitted(Permissions.ROLE_MANAGE);

  protected readonly form = this.formBuilder.group({
    username: ['', [Validators.required, Validators.maxLength(USERNAME_MAX_LENGTH)]],
    displayName: ['', [Validators.required, Validators.maxLength(DISPLAY_NAME_MAX_LENGTH)]],
  });

  protected readonly loading = signal(true);
  /** The `error.code` of the last failed load, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  protected readonly rows = signal<readonly UserSummary[]>([]);

  protected readonly adding = signal(false);
  protected readonly saving = signal(false);
  protected readonly saveFailureCode = signal<string | null>(null);
  private readonly serverFieldErrors = signal<Readonly<Record<string, string>>>({});
  private readonly attempted = signal(false);
  private readonly revision = signal(0);

  /** The account an action is in flight for, so only its own row shows a busy state. */
  protected readonly busyId = signal<string | null>(null);

  protected readonly confirmingDeactivate = signal<AccountRow | null>(null);
  protected readonly statusFailureCode = signal<string | null>(null);

  protected readonly confirmingReset = signal<AccountRow | null>(null);
  protected readonly resetFailureCode = signal<string | null>(null);

  /** A temporary password just issued, shown once. See the class-level Javadoc. */
  protected readonly passwordReveal = signal<PasswordReveal | null>(null);
  /** Whether the copy button's own label should say so, for the person who just clicked it. */
  protected readonly copied = signal(false);

  /** What just happened, said once, in the live region. */
  protected readonly announcement = signal('');

  private latestRequest = 0;

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly failed = computed(
    () => this.failureCode() !== null && this.failureCode() !== ACCESS_DENIED,
  );

  private readonly currentUserId = computed(() => this.session.user()?.userId ?? null);

  protected readonly view = computed<readonly AccountRow[]>(() =>
    this.rows().map((account) => ({
      id: account.id,
      displayName: account.displayName,
      status: account.status,
      statusLabel: labelFor(STATUS_LABELS, account.status),
      active: account.status === 'ACTIVE',
      isSelf: account.id === this.currentUserId(),
      deactivateButtonId: `account-deactivate-${account.id}`,
      reactivateButtonId: `account-reactivate-${account.id}`,
      unlockButtonId: `account-unlock-${account.id}`,
      resetButtonId: `account-reset-${account.id}`,
    })),
  );

  protected readonly fieldErrors = computed(() => {
    this.revision();
    this.attempted();
    const fromServer = this.serverFieldErrors();
    // AUTH_009 carries no `details` — it is a whole-request conflict, not a per-field one — so
    // the username field is where this screen chooses to word it, same as `saveFailure` skips it.
    const usernameTaken =
      this.saveFailureCode() === USERNAME_TAKEN
        ? 'That username is already in use at this school.'
        : null;
    return {
      username:
        fromServer['username'] ??
        usernameTaken ??
        this.messageFor('username', 'Give this person a username to sign in with.'),
      displayName:
        fromServer['displayName'] ??
        this.messageFor('displayName', 'Give this person a name the school will recognise.'),
    };
  });

  protected readonly saveFailure = computed(() => {
    const code = this.saveFailureCode();
    switch (code) {
      case null:
        return null;
      case USERNAME_TAKEN:
        return null; // Worded on the username field itself — see `fieldErrors`.
      case 'VAL_001':
        return {
          title: 'Some of these details were refused',
          detail: 'The fields marked below need correcting.',
        };
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to create an account',
          detail: 'Ask your principal to add "Manage user accounts" to your role.',
        };
      default:
        return {
          title: 'Could not create this account',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  /** Worded as the guard it is (ADR-0023/`AccessGuardrails`), never as an ordinary failure. */
  protected readonly statusFailure = computed(() => {
    const code = this.statusFailureCode();
    if (code === null) {
      return null;
    }
    if (code === LAST_ACCESS_MANAGER) {
      return {
        tone: 'info' as const,
        title: 'This would leave nobody able to manage access',
        detail:
          'Grant "Manage roles and permissions" to another active account first, then deactivate this one.',
      };
    }
    return {
      tone: 'danger' as const,
      title: 'Could not deactivate this account',
      detail: 'Nothing was changed. Check your connection and try again.',
    };
  });

  protected readonly resetFailure = computed(() => {
    const code = this.resetFailureCode();
    return code === null
      ? null
      : {
          title: 'Could not reset this password',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
  });

  constructor() {
    this.load();

    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.revision.update((count) => count + 1);
      if (Object.keys(this.serverFieldErrors()).length > 0) {
        this.serverFieldErrors.set({});
      }
    });
  }

  protected reload(): void {
    this.load();
  }

  // ── Adding an account ────────────────────────────────────────────────────────────────────

  protected startAdd(): void {
    this.attempted.set(false);
    this.saveFailureCode.set(null);
    this.serverFieldErrors.set({});
    this.form.reset({ username: '', displayName: '' }, { emitEvent: false });
    this.adding.set(true);
    this.focusAfterRender('#account-username');
  }

  protected cancelAdd(): void {
    if (this.saving()) {
      return;
    }
    this.adding.set(false);
    this.focusAfterRender('#account-add');
  }

  protected save(): void {
    if (this.saving()) {
      return;
    }
    this.attempted.set(true);
    this.form.markAllAsTouched();
    this.revision.update((count) => count + 1);
    if (this.form.invalid) {
      this.focusAfterRender(
        this.form.controls.username.invalid ? '#account-username' : '#account-display-name',
      );
      return;
    }

    const value = this.form.getRawValue();
    const request = { username: value.username.trim(), displayName: value.displayName.trim() };

    this.saving.set(true);
    this.saveFailureCode.set(null);
    this.serverFieldErrors.set({});
    this.form.disable({ emitEvent: false });

    this.api
      .createUser(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.saving.set(false);
          this.form.enable({ emitEvent: false });
          this.adding.set(false);
          this.passwordReveal.set({
            displayName: created.displayName,
            username: created.username,
            temporaryPassword: created.temporaryPassword,
            isSelf: false,
          });
          this.refresh();
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.form.enable({ emitEvent: false });
          this.saveFailureCode.set(apiErrorCode(error));
          this.serverFieldErrors.set(apiErrorDetails(error));
        },
      });
  }

  // ── Deactivate ────────────────────────────────────────────────────────────────────────────

  protected askToDeactivate(row: AccountRow): void {
    if (this.busyId() !== null) {
      return;
    }
    this.statusFailureCode.set(null);
    this.confirmingDeactivate.set(row);
  }

  protected cancelDeactivate(): void {
    const target = this.confirmingDeactivate();
    if (this.busyId() !== null || !target) {
      return;
    }
    this.confirmingDeactivate.set(null);
    this.focusAfterRender(`#${target.deactivateButtonId}`);
  }

  protected confirmDeactivate(): void {
    const target = this.confirmingDeactivate();
    if (!target || this.busyId() !== null) {
      return;
    }
    this.busyId.set(target.id);
    this.statusFailureCode.set(null);

    this.api
      .deactivate(target.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busyId.set(null);
          this.confirmingDeactivate.set(null);
          if (target.isSelf) {
            // The server has already ended this session (ADR-0023). Leaving this tab on a roster
            // it can no longer refresh would just wait for the next click to discover that.
            this.session.signedOut();
            void this.router.navigate(['/login']);
            return;
          }
          this.announcement.set(`${target.displayName}'s account has been deactivated.`);
          this.refresh(() => this.focusAfterRender(`#${target.reactivateButtonId}`));
        },
        error: (error: unknown) => {
          this.busyId.set(null);
          this.statusFailureCode.set(apiErrorCode(error));
        },
      });
  }

  // ── Reactivate ────────────────────────────────────────────────────────────────────────────

  protected reactivate(row: AccountRow): void {
    if (this.busyId() !== null) {
      return;
    }
    this.busyId.set(row.id);

    this.api
      .reactivate(row.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busyId.set(null);
          this.announcement.set(`${row.displayName}'s account is active again.`);
          this.refresh(() => this.focusAfterRender(`#${row.deactivateButtonId}`));
        },
        error: (error: unknown) => {
          this.busyId.set(null);
          this.announcement.set('');
          this.failureCode.set(null);
          this.statusFailureCode.set(apiErrorCode(error));
        },
      });
  }

  // ── Unlock ────────────────────────────────────────────────────────────────────────────────

  protected unlock(row: AccountRow): void {
    if (this.busyId() !== null) {
      return;
    }
    this.busyId.set(row.id);

    this.api
      .unlock(row.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busyId.set(null);
          // Whether the account was actually locked is not something this response distinguishes
          // (see the class Javadoc), so this says what is now true rather than guessing at a change.
          this.announcement.set(`Any lockout on ${row.displayName}'s account has been cleared.`);
          this.focusAfterRender(`#${row.unlockButtonId}`);
        },
        error: () => {
          this.busyId.set(null);
          this.announcement.set(`Could not clear a lockout on ${row.displayName}'s account.`);
        },
      });
  }

  // ── Reset password ───────────────────────────────────────────────────────────────────────

  protected askToReset(row: AccountRow): void {
    if (this.busyId() !== null) {
      return;
    }
    this.resetFailureCode.set(null);
    this.confirmingReset.set(row);
  }

  protected cancelReset(): void {
    const target = this.confirmingReset();
    if (this.busyId() !== null || !target) {
      return;
    }
    this.confirmingReset.set(null);
    this.focusAfterRender(`#${target.resetButtonId}`);
  }

  protected confirmReset(): void {
    const target = this.confirmingReset();
    if (!target || this.busyId() !== null) {
      return;
    }
    this.busyId.set(target.id);
    this.resetFailureCode.set(null);

    this.api
      .resetPassword(target.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.busyId.set(null);
          this.confirmingReset.set(null);
          this.passwordReveal.set({
            displayName: target.displayName,
            username: null,
            temporaryPassword: result.temporaryPassword,
            isSelf: target.isSelf,
          });
        },
        error: (error: unknown) => {
          this.busyId.set(null);
          this.resetFailureCode.set(apiErrorCode(error));
        },
      });
  }

  /**
   * The one and only way the password reveal panel closes.
   *
   * Not wired to Escape or a scrim click — see the template — because there is nothing to go back
   * to: the password will not be shown again regardless of how this closes, so the only question
   * worth asking is whether the admin has copied it, which this button is the answer to.
   */
  protected acknowledgeReveal(): void {
    const reveal = this.passwordReveal();
    this.passwordReveal.set(null);
    this.copied.set(false);
    if (reveal?.isSelf) {
      this.session.signedOut();
      void this.router.navigate(['/login']);
      return;
    }
    this.focusAfterRender('#account-add');
  }

  /**
   * Best-effort only. `navigator.clipboard` can be absent (an insecure context, an older browser,
   * a permission denial) and the password is already fully visible and selectable on screen either
   * way, so a failure here is silent rather than another banner on top of a screen about a secret.
   */
  protected copyPassword(password: string): void {
    navigator.clipboard
      ?.writeText(password)
      .then(() => {
        this.copied.set(true);
        setTimeout(() => this.copied.set(false), 2000);
      })
      .catch(() => {
        // Nothing to do: the text is already selectable in the panel.
      });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private load(): void {
    const request = ++this.latestRequest;
    this.loading.set(true);
    this.failureCode.set(null);

    this.api
      .users()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.rows.set(result);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.rows.set([]);
          this.loading.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  /** Re-read the roster after a write, without blanking the screen — a failure here leaves rows stale, not wrong. */
  private refresh(then?: () => void): void {
    const request = ++this.latestRequest;
    this.api
      .users()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          if (request === this.latestRequest) {
            this.rows.set(result);
          }
          then?.();
        },
        error: () => then?.(),
      });
  }

  private messageFor(name: 'username' | 'displayName', required: string): string | null {
    const control = this.form.controls[name];
    if (!control.touched && !this.attempted()) {
      return null;
    }
    if (control.hasError('required')) {
      return required;
    }
    if (control.hasError('maxlength')) {
      const max = name === 'username' ? USERNAME_MAX_LENGTH : DISPLAY_NAME_MAX_LENGTH;
      return `That is longer than ${max} characters.`;
    }
    return null;
  }

  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
