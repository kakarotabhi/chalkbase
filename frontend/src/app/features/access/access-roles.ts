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
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  FormControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import { IdentityAccessApi } from '../../core/api/identity-access-api';
import {
  GrantResponse,
  PermissionDefinition,
  RoleResponse,
  ScopeType,
  UserSummary,
} from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { SessionStore, permitted } from '../../core/auth/session-store';
import { Button } from '../../shared/components/button/button';
import { Checkbox } from '../../shared/components/checkbox/checkbox';
import { Dialog } from '../../shared/components/dialog/dialog';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select, SelectOption } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';
import {
  ACCESS_DENIED,
  CANNOT_GRANT_UNHELD_PERMISSION,
  GRANT_ALREADY_EXISTS,
  LAST_ACCESS_MANAGER,
  ROLE_NAME_TAKEN,
  SCOPE_OPTIONS,
  STATUS_LABELS,
  groupByModule,
  labelFor,
  moduleLabel,
  scopeNeedsId,
} from './access-shared';

/** `role.name` is `varchar(120)`. */
const NAME_MAX_LENGTH = 120;
/** `role.description` is `varchar(400)`. */
const DESCRIPTION_MAX_LENGTH = 400;

/** null when closed, 'new' when creating a role, or the id of the role being edited. */
type RoleEditor = 'new' | string | null;

/** One role, with everything the template needs already decided. */
interface RoleRow {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly templateCode: string | null;
  readonly permissionCount: number;
  readonly editButtonId: string;
  readonly holdersButtonId: string;
}

/** One row of the permission catalogue, as the editor shows it. */
interface PermissionRow {
  readonly code: string;
  readonly label: string;
  readonly description: string;
  readonly held: boolean;
}

/**
 * Roles, the permission catalogue, and grants (`docs/status.md`: "Roles and permissions").
 *
 * ## Nobody can check a box for a permission they do not hold, and this screen says so before the
 * server has to
 *
 * `AccessGuardrails#requireHeldByActor` refuses to let a holder of `identity:role:manage` create a
 * role, add to one, or grant one carrying a permission their own session does not currently have —
 * `AUTH_011`. Checkboxes are never disabled for this (unchecking an already-granted permission must
 * stay possible even if the actor no longer holds it themselves — removing access is never
 * guarded), so instead every row that the actor does not hold says so, and `unheldOf` computes —
 * from the same submission the server would see — exactly which of the permissions *being added*
 * are the problem, before the request is even sent. The same computation reads an `AUTH_011` that
 * gets through anyway (a permission catalogue can change between page load and submit).
 *
 * ## A role's permission set is replaced wholesale, never patched
 *
 * `saveRolePermissions` always sends the *complete* set the editor currently shows checked, not a
 * delta — `PUT /api/access/roles/{id}/permissions` replaces the row outright, and a client that
 * sent only what changed would silently delete the rest.
 *
 * ## `AUTH_010` is a guard, not a failure
 *
 * Dropping `identity:role:manage` from a role that is the school's only path to managing access is
 * refused, the same way deactivating the last such account is on the roster screen — see
 * `roleSaveFailure`.
 *
 * ## The grants panel needs to know *whose* grants, and this screen may not be able to ask
 *
 * `identity:role:manage` and `identity:user:read` are separate permissions (the user roster is
 * gated on the second). A role manager who does not also hold it cannot list accounts to pick from,
 * so the picker degrades to a plain id field when `loadPickableUsers` fails — see `pickableUsers`.
 * The roster screen's "Manage roles" link is the normal way in, and carries only an id in its query
 * parameter, never a name: a display name is Confidential (ADR-0014) and has no business in a URL.
 *
 * ## There is no guard on this route, deliberately
 *
 * ADR-0008: the server leaves the menu item out for anyone without `identity:role:manage`, and
 * every endpoint here enforces it independently. Typing the URL without it lands here and gets a
 * 403 this screen explains, exactly as the audit log does.
 */
@Component({
  selector: 'cb-access-roles',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Button,
    Checkbox,
    Dialog,
    FormField,
    Select,
    TextInput,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './access-roles.html',
  styleUrl: './access-roles.scss',
})
export class AccessRoles {
  private readonly api = inject(IdentityAccessApi);
  private readonly session = inject(SessionStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly scopeOptions = SCOPE_OPTIONS;
  /** Whether to offer a link back to the account roster — a different permission, `identity:user:read`. */
  protected readonly canViewUsers = permitted(Permissions.USER_READ);

  // ── Loading the screen ───────────────────────────────────────────────────────────────────

  protected readonly loading = signal(true);
  /** The `error.code` of the last failed load, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  protected readonly permissions = signal<readonly PermissionDefinition[]>([]);
  protected readonly roles = signal<readonly RoleResponse[]>([]);

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly failed = computed(
    () => this.failureCode() !== null && this.failureCode() !== ACCESS_DENIED,
  );

  protected readonly permissionsByModule = computed(() => groupByModule(this.permissions()));
  protected readonly moduleNames = computed(() => [...this.permissionsByModule().keys()]);
  protected readonly moduleLabel = moduleLabel;

  protected readonly roleRows = computed<readonly RoleRow[]>(() =>
    this.roles()
      .map((role) => ({
        id: role.id,
        name: role.name,
        description: role.description ?? '',
        templateCode: role.templateCode ?? null,
        permissionCount: role.permissions.length,
        editButtonId: `role-edit-${role.id}`,
        holdersButtonId: `role-holders-${role.id}`,
      }))
      .sort((a, b) => a.name.localeCompare(b.name)),
  );

  /** The role list, as options for the "grant a role" select. */
  protected readonly roleOptions = computed<readonly SelectOption[]>(() => [
    { value: '', label: 'Choose a role' },
    ...this.roleRows().map((row) => ({ value: row.id, label: row.name })),
  ]);

  /** What just happened, said once, in the live region. */
  protected readonly announcement = signal('');

  constructor() {
    this.loadScreen();
    this.loadPickableUsers();

    // The roster link carries only an id (see the class Javadoc). Reading it once at
    // construction is enough: nothing else on this screen changes the URL other than this
    // component's own `selectAccount`.
    const userId = this.route.snapshot.queryParamMap.get('user');
    if (userId) {
      this.selectAccount(userId);
    }

    // A reactive form is not a signal, so `roleFieldErrors` needs something to depend on or it
    // would compute once and never notice a keystroke or a blur under it — same reason
    // `subjects.ts` bumps a revision counter on `valueChanges`.
    this.roleForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.roleRevision.update((count) => count + 1);
      if (Object.keys(this.roleServerFieldErrors()).length > 0) {
        this.roleServerFieldErrors.set({});
      }
    });
  }

  protected reload(): void {
    this.loadScreen();
  }

  private loadScreen(): void {
    this.loading.set(true);
    this.failureCode.set(null);

    forkJoin({
      permissions: this.api.permissions(),
      roles: this.api.roles(),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ permissions, roles }) => {
          this.permissions.set(permissions);
          this.roles.set(roles);
          this.loading.set(false);
          this.rebuildPermissionControls();
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  // ── The permission editor, shared by "create a role" and "edit a role" ──────────────────

  /** One `FormControl<boolean>` per catalogue permission, rebuilt whenever the catalogue is. */
  private permissionControls = new Map<string, FormControl<boolean>>();

  protected permissionControl(code: string): FormControl<boolean> {
    let control = this.permissionControls.get(code);
    if (!control) {
      control = this.formBuilder.control(false);
      this.permissionControls.set(code, control);
    }
    return control;
  }

  private rebuildPermissionControls(): void {
    const codes = new Set(this.permissions().map((permission) => permission.code));
    for (const code of [...this.permissionControls.keys()]) {
      if (!codes.has(code)) {
        this.permissionControls.delete(code);
      }
    }
    for (const code of codes) {
      this.permissionControl(code);
    }
  }

  protected permissionRows(moduleName: string): readonly PermissionRow[] {
    return (this.permissionsByModule().get(moduleName) ?? []).map((permission) => ({
      code: permission.code,
      label: permission.label,
      description: permission.description,
      held: this.session.hasCode(permission.code),
    }));
  }

  private setCheckedPermissions(codes: readonly string[]): void {
    const checked = new Set(codes);
    for (const [code, control] of this.permissionControls) {
      control.setValue(checked.has(code), { emitEvent: false });
    }
  }

  private checkedPermissionCodes(): readonly string[] {
    return [...this.permissionControls]
      .filter(([, control]) => control.value)
      .map(([code]) => code);
  }

  /**
   * Which of `requested` the acting account cannot grant — computed client-side from the same
   * session data `AccessGuardrails#requireHeldByActor` checks server-side, so this screen can name
   * the permission before the request is ever sent (or after an `AUTH_011` that got through anyway
   * because the catalogue changed underneath the page).
   */
  private unheldOf(requested: readonly string[]): readonly PermissionDefinition[] {
    const byCode = new Map(this.permissions().map((permission) => [permission.code, permission]));
    return requested
      .filter((code) => !this.session.hasCode(code))
      .map((code) => byCode.get(code))
      .filter((permission): permission is PermissionDefinition => permission !== undefined);
  }

  // ── Creating a role ───────────────────────────────────────────────────────────────────────

  protected readonly roleEditing = signal<RoleEditor>(null);
  protected readonly roleSaving = signal(false);
  protected readonly roleSaveFailureCode = signal<string | null>(null);
  protected readonly unheldRequested = signal<readonly PermissionDefinition[]>([]);
  private readonly roleServerFieldErrors = signal<Readonly<Record<string, string>>>({});
  private readonly roleAttempted = signal(false);
  private readonly roleRevision = signal(0);

  protected readonly roleForm = this.formBuilder.group({
    name: ['', [Validators.required, Validators.maxLength(NAME_MAX_LENGTH)]],
    description: ['', [Validators.maxLength(DESCRIPTION_MAX_LENGTH)]],
  });

  protected readonly roleAdding = computed(() => this.roleEditing() === 'new');
  protected readonly roleEditingId = computed(() => {
    const editing = this.roleEditing();
    return editing === 'new' ? null : editing;
  });
  protected readonly roleEditorHeading = computed(() =>
    this.roleAdding() ? 'Add a role' : 'Edit permissions',
  );

  protected readonly roleFieldErrors = computed(() => {
    this.roleRevision();
    this.roleAttempted();
    const fromServer = this.roleServerFieldErrors();
    const nameTaken =
      this.roleSaveFailureCode() === ROLE_NAME_TAKEN
        ? 'A role with a name this close to that one already exists.'
        : null;
    return {
      name: fromServer['name'] ?? nameTaken ?? this.roleNameMessage(),
      description: fromServer['description'] ?? this.roleDescriptionMessage(),
    };
  });

  protected readonly roleSaveFailure = computed(() => {
    const code = this.roleSaveFailureCode();
    if (code === null || code === ROLE_NAME_TAKEN) {
      return null; // Worded on the name field — see `roleFieldErrors`.
    }
    if (code === CANNOT_GRANT_UNHELD_PERMISSION) {
      const names = this.unheldRequested().map((permission) => permission.label);
      return {
        tone: 'danger' as const,
        title:
          names.length === 1
            ? `You cannot grant "${names[0]}" because you do not hold it yourself`
            : `You cannot grant ${names.length} permissions you do not hold yourself`,
        detail:
          names.length > 1
            ? `${names.join(', ')}. Ask someone who holds them to add them to this role, or leave those boxes unchecked.`
            : 'Ask someone who holds it to add it to this role, or leave that box unchecked.',
      };
    }
    if (code === LAST_ACCESS_MANAGER) {
      return {
        tone: 'info' as const,
        title: 'This would leave nobody able to manage access',
        detail:
          'This role is the only path to "Manage roles and permissions" for at least one account. Grant that permission to another active account before removing it here.',
      };
    }
    if (code === 'VAL_001') {
      return {
        tone: 'danger' as const,
        title: 'Some of these details were refused',
        detail: 'Check the fields below.',
      };
    }
    if (code === ACCESS_DENIED) {
      return {
        tone: 'danger' as const,
        title: 'You do not have permission to manage roles',
        detail: 'Ask your principal to add "Manage roles and permissions" to your role.',
      };
    }
    return {
      tone: 'danger' as const,
      title: 'Could not save this role',
      detail: 'Nothing was changed. Check your connection and try again.',
    };
  });

  protected startAddRole(): void {
    this.roleAttempted.set(false);
    this.roleSaveFailureCode.set(null);
    this.roleServerFieldErrors.set({});
    this.unheldRequested.set([]);
    this.roleForm.reset({ name: '', description: '' }, { emitEvent: false });
    this.setCheckedPermissions([]);
    this.roleEditing.set('new');
    this.focusAfterRender('#role-name');
  }

  protected startEditRole(row: RoleRow): void {
    const role = this.roles().find((candidate) => candidate.id === row.id);
    if (!role) {
      return;
    }
    this.roleAttempted.set(false);
    this.roleSaveFailureCode.set(null);
    this.roleServerFieldErrors.set({});
    this.unheldRequested.set([]);
    this.roleForm.reset(
      { name: role.name, description: role.description ?? '' },
      { emitEvent: false },
    );
    this.setCheckedPermissions(role.permissions);
    this.roleEditing.set(role.id);
    this.focusAfterRender('#role-name');
  }

  protected cancelRoleEdit(): void {
    if (this.roleSaving()) {
      return;
    }
    const id = this.roleEditingId();
    this.roleEditing.set(null);
    this.focusAfterRender(id ? `#role-edit-${id}` : '#role-add');
  }

  protected saveRole(): void {
    if (this.roleSaving()) {
      return;
    }
    this.roleAttempted.set(true);
    this.roleForm.markAllAsTouched();
    this.roleRevision.update((count) => count + 1);
    if (this.roleForm.invalid) {
      this.focusAfterRender(
        this.roleForm.controls.name.invalid ? '#role-name' : '#role-description',
      );
      return;
    }

    const editingId = this.roleEditingId();
    const checked = this.checkedPermissionCodes();
    const previouslyGranted = editingId
      ? (this.roles().find((role) => role.id === editingId)?.permissions ?? [])
      : [];
    const adding = checked.filter((code) => !previouslyGranted.includes(code));
    const notHeld = this.unheldOf(adding);
    if (notHeld.length > 0) {
      this.unheldRequested.set(notHeld);
      this.roleSaveFailureCode.set(CANNOT_GRANT_UNHELD_PERMISSION);
      return;
    }

    const value = this.roleForm.getRawValue();
    this.roleSaving.set(true);
    this.roleSaveFailureCode.set(null);
    this.roleServerFieldErrors.set({});
    this.unheldRequested.set([]);
    this.roleForm.disable({ emitEvent: false });

    const call = editingId
      ? this.api.updateRolePermissions(editingId, { permissions: checked })
      : this.api.createRole({
          name: value.name.trim(),
          description: value.description.trim() || undefined,
          permissions: checked,
        });

    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (role) => {
        this.roleSaving.set(false);
        this.roleForm.enable({ emitEvent: false });
        this.roleEditing.set(null);
        this.announcement.set(editingId ? `${role.name} was saved.` : `${role.name} was added.`);
        this.refreshRoles(() => this.focusAfterRender(`#role-edit-${role.id}`));
      },
      error: (error: unknown) => {
        this.roleSaving.set(false);
        this.roleForm.enable({ emitEvent: false });
        const code = apiErrorCode(error);
        this.roleSaveFailureCode.set(code);
        this.roleServerFieldErrors.set(apiErrorDetails(error));
        if (code === CANNOT_GRANT_UNHELD_PERMISSION) {
          this.unheldRequested.set(this.unheldOf(adding));
        }
      },
    });
  }

  private roleNameMessage(): string | null {
    const control = this.roleForm.controls.name;
    if (!control.touched && !this.roleAttempted()) {
      return null;
    }
    if (control.hasError('required')) {
      return "Give the role a name, for example 'Front Office'.";
    }
    if (control.hasError('maxlength')) {
      return `A role name is ${NAME_MAX_LENGTH} characters or fewer.`;
    }
    return null;
  }

  private roleDescriptionMessage(): string | null {
    const control = this.roleForm.controls.description;
    if ((!control.touched && !this.roleAttempted()) || !control.hasError('maxlength')) {
      return null;
    }
    return `A description is ${DESCRIPTION_MAX_LENGTH} characters or fewer.`;
  }

  // ── Who holds this role ──────────────────────────────────────────────────────────────────

  protected readonly openHoldersFor = signal<string | null>(null);
  protected readonly holdersLoading = signal(false);
  protected readonly holdersFailed = signal(false);
  protected readonly holders = signal<readonly UserSummary[]>([]);

  protected toggleHolders(row: RoleRow): void {
    if (this.openHoldersFor() === row.id) {
      this.openHoldersFor.set(null);
      return;
    }
    this.openHoldersFor.set(row.id);
    this.holdersLoading.set(true);
    this.holdersFailed.set(false);
    this.holders.set([]);

    this.api
      .holders(row.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.holdersLoading.set(false);
          this.holders.set(result);
        },
        error: () => {
          this.holdersLoading.set(false);
          this.holdersFailed.set(true);
        },
      });
  }

  protected statusLabel(status: string): string {
    return labelFor(STATUS_LABELS, status);
  }

  // ── Grants: pick an account ──────────────────────────────────────────────────────────────

  /**
   * The roster, for the account picker — `null` while loading, `[]` (with `pickableUsersFailed`
   * true) if `identity:user:read` is not held. See the class Javadoc.
   */
  protected readonly pickableUsers = signal<readonly UserSummary[] | null>(null);
  protected readonly pickableUsersFailed = signal(false);

  protected readonly accountIdControl = this.formBuilder.control('');

  protected readonly accountOptions = computed<readonly SelectOption[]>(() => [
    { value: '', label: 'Choose an account' },
    ...(this.pickableUsers() ?? []).map((account) => ({
      value: account.id,
      label: `${account.displayName} — ${this.statusLabel(account.status)}`,
    })),
  ]);

  protected readonly selectedAccountName = computed(() => {
    const id = this.accountIdControl.value;
    return this.pickableUsers()?.find((account) => account.id === id)?.displayName ?? null;
  });

  private loadPickableUsers(): void {
    this.api
      .users()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => this.pickableUsers.set(result),
        error: () => {
          this.pickableUsers.set([]);
          this.pickableUsersFailed.set(true);
        },
      });
  }

  protected selectAccount(accountId: string): void {
    this.accountIdControl.setValue(accountId);
    if (!accountId) {
      this.grants.set([]);
      return;
    }
    // Kept in the URL — never the name beside it — so the link this screen is under can be
    // shared or bookmarked the same way arriving from the roster's own link works.
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { user: accountId },
      queryParamsHandling: 'merge',
    });
    this.loadGrants(accountId);
  }

  // ── Grants: this account's own grants ────────────────────────────────────────────────────

  protected readonly grants = signal<readonly GrantResponse[]>([]);
  protected readonly grantsLoading = signal(false);
  protected readonly grantsFailureCode = signal<string | null>(null);

  protected readonly grantAdding = signal(false);
  protected readonly grantSaving = signal(false);
  protected readonly grantSaveFailureCode = signal<string | null>(null);
  protected readonly grantUnheld = signal<readonly PermissionDefinition[]>([]);

  protected readonly grantForm = this.formBuilder.group({
    roleId: ['', Validators.required],
    scopeType: ['SCHOOL', Validators.required],
    scopeId: [''],
    validFrom: [''],
    validTo: [''],
  });

  // A reactive form is not a signal, so `grantScopeNeedsId` needs something to depend on or it
  // would compute once and never notice the scope type changing under it.
  private readonly scopeTypeValue = toSignal(this.grantForm.controls.scopeType.valueChanges, {
    initialValue: this.grantForm.controls.scopeType.value,
  });

  protected readonly grantScopeNeedsId = computed(() => scopeNeedsId(this.scopeTypeValue()));

  protected readonly revokingGrant = signal<GrantResponse | null>(null);
  protected readonly revokeFailureCode = signal<string | null>(null);

  protected onGrantSubmit(event: Event): void {
    event.preventDefault();
    this.saveGrant();
  }

  private loadGrants(accountId: string): void {
    this.grantsLoading.set(true);
    this.grantsFailureCode.set(null);
    this.grants.set([]);

    this.api
      .grants(accountId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.grantsLoading.set(false);
          this.grants.set(result);
        },
        error: (error: unknown) => {
          this.grantsLoading.set(false);
          this.grantsFailureCode.set(apiErrorCode(error));
        },
      });
  }

  protected startAddGrant(): void {
    this.grantSaveFailureCode.set(null);
    this.grantUnheld.set([]);
    this.grantForm.reset(
      { roleId: '', scopeType: 'SCHOOL', scopeId: '', validFrom: '', validTo: '' },
      { emitEvent: false },
    );
    this.grantAdding.set(true);
  }

  protected cancelAddGrant(): void {
    if (this.grantSaving()) {
      return;
    }
    this.grantAdding.set(false);
  }

  protected saveGrant(): void {
    const accountId = this.accountIdControl.value;
    if (this.grantSaving() || !accountId || this.grantForm.invalid) {
      this.grantForm.markAllAsTouched();
      return;
    }

    const value = this.grantForm.getRawValue();
    const role = this.roles().find((candidate) => candidate.id === value.roleId);
    const notHeld = role ? this.unheldOf(role.permissions) : [];
    if (notHeld.length > 0) {
      this.grantUnheld.set(notHeld);
      this.grantSaveFailureCode.set(CANNOT_GRANT_UNHELD_PERMISSION);
      return;
    }

    this.grantSaving.set(true);
    this.grantSaveFailureCode.set(null);
    this.grantUnheld.set([]);

    this.api
      .grantRole(accountId, {
        roleId: value.roleId,
        // The form control itself is untyped `string` — a reactive form has no closed-union
        // controls — but its only values are `scopeOptions`' own, which are `ScopeType`'s.
        scopeType: value.scopeType as ScopeType,
        scopeId: scopeNeedsId(value.scopeType) ? value.scopeId || undefined : undefined,
        validFrom: value.validFrom || undefined,
        validTo: value.validTo || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.grantSaving.set(false);
          this.grantAdding.set(false);
          this.announcement.set(`${role?.name ?? 'That role'} has been granted.`);
          this.loadGrants(accountId);
        },
        error: (error: unknown) => {
          this.grantSaving.set(false);
          const code = apiErrorCode(error);
          this.grantSaveFailureCode.set(code);
          if (code === CANNOT_GRANT_UNHELD_PERMISSION && role) {
            this.grantUnheld.set(this.unheldOf(role.permissions));
          }
        },
      });
  }

  protected readonly grantSaveFailure = computed(() => {
    const code = this.grantSaveFailureCode();
    if (code === null) {
      return null;
    }
    if (code === CANNOT_GRANT_UNHELD_PERMISSION) {
      const names = this.grantUnheld().map((permission) => permission.label);
      return {
        title: 'You cannot grant this role',
        detail:
          names.length > 0
            ? `It carries ${names.join(', ')}, which you do not hold yourself. Ask someone who does to grant it, or add ${names.length === 1 ? 'that permission' : 'those permissions'} to your own role first.`
            : 'It carries a permission you do not hold yourself.',
      };
    }
    if (code === GRANT_ALREADY_EXISTS) {
      return {
        title: 'That role is already granted',
        detail: 'This account already holds this role at this scope.',
      };
    }
    if (code === 'VAL_001') {
      return {
        title: 'That grant could not be saved',
        detail: 'Check the role, scope and dates above.',
      };
    }
    return {
      title: 'Could not grant this role',
      detail: 'Nothing was changed. Check your connection and try again.',
    };
  });

  protected askToRevoke(grant: GrantResponse): void {
    this.revokeFailureCode.set(null);
    this.revokingGrant.set(grant);
  }

  protected cancelRevoke(): void {
    this.revokingGrant.set(null);
  }

  protected confirmRevoke(): void {
    const grant = this.revokingGrant();
    const accountId = this.accountIdControl.value;
    if (!grant || !accountId) {
      return;
    }
    this.revokeFailureCode.set(null);

    this.api
      .revokeGrant(accountId, grant.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.revokingGrant.set(null);
          this.announcement.set(`${grant.roleName} has been revoked.`);
          this.loadGrants(accountId);
        },
        error: (error: unknown) => {
          this.revokeFailureCode.set(apiErrorCode(error));
        },
      });
  }

  protected readonly revokeFailure = computed(() => {
    const code = this.revokeFailureCode();
    if (code === null) {
      return null;
    }
    if (code === LAST_ACCESS_MANAGER) {
      return {
        tone: 'info' as const,
        title: 'This would leave nobody able to manage access',
        detail: 'Grant "Manage roles and permissions" to another active account first.',
      };
    }
    return {
      tone: 'danger' as const,
      title: 'Could not revoke this grant',
      detail: 'Nothing was changed. Check your connection and try again.',
    };
  });

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private refreshRoles(then?: () => void): void {
    this.api
      .roles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.roles.set(result);
          then?.();
        },
        error: () => then?.(),
      });
  }

  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
