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
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import { Board, SchoolProfile as SchoolProfileModel } from '../../core/api/models';
import { SchoolApi } from '../../core/api/school-api';
import { HasUnsavedChanges } from '../../core/forms/unsaved-changes-guard';
import { Button } from '../../shared/components/button/button';
import { FormField } from '../../shared/components/form-field/form-field';
import { Select, SelectOption } from '../../shared/components/select/select';
import { TextInput } from '../../shared/components/text-input/text-input';

/**
 * The same rules the backend enforces, so a mistake is caught before the round trip rather than
 * after it. Duplicated deliberately, and they must not drift: the server is the authority and still
 * rejects anything that gets past these.
 */
const PINCODE_PATTERN = /^[1-9][0-9]{5}$/;
const PHONE_PATTERN = /^[+0-9][0-9 ()-]{6,19}$/;
const WEBSITE_PATTERN = /^https?:\/\/\S+$/;

/** Boards, under the names Indian schools actually use for them. */
const BOARDS: readonly SelectOption[] = [
  { value: 'CBSE', label: 'CBSE' },
  { value: 'CISCE', label: 'CISCE (ICSE / ISC)' },
  { value: 'STATE', label: 'State board' },
  { value: 'IB', label: 'International Baccalaureate' },
  { value: 'CAIE', label: 'Cambridge (CAIE)' },
  { value: 'OTHER', label: 'Other' },
];

/**
 * States and union territories.
 *
 * TODO(reference-data): this is Tier-1 master data and belongs in `public` as shared reference
 * data read through an endpoint (ADR-0006). There is no such endpoint yet and the backend stores a
 * plain string, so the list lives here to keep the control a picker rather than a free-text box in
 * which every school spells Maharashtra differently. It moves wholesale when the table lands.
 */
const STATES: readonly SelectOption[] = [
  'Andaman and Nicobar Islands',
  'Andhra Pradesh',
  'Arunachal Pradesh',
  'Assam',
  'Bihar',
  'Chandigarh',
  'Chhattisgarh',
  'Dadra and Nagar Haveli and Daman and Diu',
  'Delhi',
  'Goa',
  'Gujarat',
  'Haryana',
  'Himachal Pradesh',
  'Jammu and Kashmir',
  'Jharkhand',
  'Karnataka',
  'Kerala',
  'Ladakh',
  'Lakshadweep',
  'Madhya Pradesh',
  'Maharashtra',
  'Manipur',
  'Meghalaya',
  'Mizoram',
  'Nagaland',
  'Odisha',
  'Puducherry',
  'Punjab',
  'Rajasthan',
  'Sikkim',
  'Tamil Nadu',
  'Telangana',
  'Tripura',
  'Uttar Pradesh',
  'Uttarakhand',
  'West Bengal',
].map((name) => ({ value: name, label: name }));

/** Every editable control. `code` is not one of them — it identifies the tenant (ADR-0011). */
type FieldName =
  | 'name'
  | 'board'
  | 'affiliationNumber'
  | 'addressLine1'
  | 'addressLine2'
  | 'city'
  | 'state'
  | 'pincode'
  | 'principalName'
  | 'phone'
  | 'email'
  | 'website';

interface ProfileFormValue {
  code: string;
  name: string;
  board: string;
  affiliationNumber: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  pincode: string;
  principalName: string;
  phone: string;
  email: string;
  website: string;
}

const EMPTY: ProfileFormValue = {
  code: '',
  name: '',
  board: '',
  affiliationNumber: '',
  addressLine1: '',
  addressLine2: '',
  city: '',
  state: '',
  pincode: '',
  principalName: '',
  phone: '',
  email: '',
  website: '',
};

/**
 * The id on each control, so a failed submit can put focus on the first field that needs fixing.
 * In the order the form reads, which is the order someone would have filled it in.
 */
const FIELD_IDS: Readonly<Record<FieldName, string>> = {
  name: 'school-name',
  board: 'school-board',
  affiliationNumber: 'school-affiliation',
  addressLine1: 'school-address1',
  addressLine2: 'school-address2',
  city: 'school-city',
  state: 'school-state',
  pincode: 'school-pincode',
  principalName: 'school-principal',
  phone: 'school-phone',
  email: 'school-email',
  website: 'school-website',
};

const FIELD_ORDER: readonly FieldName[] = [
  'name',
  'board',
  'affiliationNumber',
  'addressLine1',
  'addressLine2',
  'city',
  'state',
  'pincode',
  'principalName',
  'phone',
  'email',
  'website',
];

/** What to say when a required field is empty, in the words of the thing that is missing. */
const REQUIRED: Readonly<Record<string, string>> = {
  name: 'Enter the school name.',
  board: 'Choose the board this school is affiliated to.',
  addressLine1: 'Enter the first line of the address.',
  city: 'Enter the city or town.',
  state: 'Choose the state.',
  pincode: 'Enter the PIN code.',
  principalName: "Enter the principal's name.",
  phone: 'Enter a phone number.',
  email: 'Enter an e-mail address.',
};

/** What to say when a field is filled in but not in a shape the backend will accept. */
const MALFORMED: Readonly<Record<string, string>> = {
  pincode: 'A PIN code is six digits and does not start with a zero.',
  phone: 'Use digits, spaces, brackets or dashes — for example +91 20 2721 0000.',
  website: 'Include the https:// at the front, for example https://school.example.',
};

/**
 * The school's own profile: the first screen where a school edits its master data.
 *
 * ## Why there is no id anywhere here
 *
 * The tenant is the school (ADR-0011), so the endpoint is `/api/school/profile` — singular, no id,
 * no route parameter. This screen cannot address another school even by accident, because there is
 * nowhere to put the school it would address.
 *
 * ## The code is shown and cannot be changed
 *
 * The approved design draws the school code as an ordinary field with a duplicate-code error under
 * it. That error belongs to the onboarding form, not to this one: once the schema exists, the code
 * addresses the tenant and the schema name is where every row lives, so neither can be edited
 * (ADR-0011). The field stays because a principal needs to read the code off the screen when a
 * parent cannot sign in — it is rendered read-only, and both values are sent back on save so the
 * server refuses an attempt to change them rather than ignoring it.
 */
@Component({
  selector: 'cb-school-profile',
  imports: [ReactiveFormsModule, FormField, TextInput, Select, Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './school-profile.html',
  styleUrl: './school-profile.scss',
})
export class SchoolProfile implements HasUnsavedChanges {
  private readonly schoolApi = inject(SchoolApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly boards = BOARDS;
  protected readonly states = STATES;
  protected readonly fieldIds = FIELD_IDS;
  /** Not in `FIELD_IDS`: the code is not an editable field, so nothing focuses or validates it. */
  protected readonly codeFieldId = 'school-code';

  protected readonly form = this.formBuilder.group({
    // Disabled rather than hidden: it is the school's identity and cannot be edited (ADR-0011). A
    // disabled control stays out of `value` and out of `dirty`, and is still in `getRawValue()`.
    code: [{ value: '', disabled: true }],
    name: ['', [Validators.required, Validators.maxLength(200)]],
    board: ['', Validators.required],
    affiliationNumber: ['', Validators.maxLength(40)],
    addressLine1: ['', [Validators.required, Validators.maxLength(200)]],
    addressLine2: ['', Validators.maxLength(200)],
    city: ['', [Validators.required, Validators.maxLength(100)]],
    state: ['', Validators.required],
    pincode: ['', [Validators.required, Validators.pattern(PINCODE_PATTERN)]],
    principalName: ['', [Validators.required, Validators.maxLength(200)]],
    phone: ['', [Validators.required, Validators.pattern(PHONE_PATTERN)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    website: ['', Validators.pattern(WEBSITE_PATTERN)],
  });

  protected readonly loading = signal(true);
  protected readonly loadFailed = signal(false);
  protected readonly saving = signal(false);
  protected readonly savedJustNow = signal(false);
  /** False until the school has saved a profile once, so the screen can say so. */
  protected readonly configured = signal(true);
  /** The `error.code` of the last failed save, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  private readonly attempted = signal(false);

  /**
   * Field messages the server sent back, keyed by field.
   *
   * Cleared as soon as anything is edited: they describe the payload that was refused, and one
   * changed character makes them about a request that no longer exists.
   */
  private readonly serverErrors = signal<Readonly<Record<string, string>>>({});

  /** What the server last confirmed. Cancel returns here, and `dirty` is measured against it. */
  private saved: ProfileFormValue = EMPTY;

  /** Echoed back on save so a changed one is refused. Never shown: it is a database detail. */
  private schemaName = '';

  private readonly formEvents = toSignal(this.form.events, { initialValue: null });

  /** True while the form holds edits nobody has saved. Drives the unsaved-changes bar. */
  protected readonly dirty = computed(() => {
    this.formEvents();
    return this.form.dirty;
  });

  protected readonly fieldErrors = computed(() => {
    // Read so the messages recompute as the form changes; the values come off the controls.
    this.formEvents();
    this.attempted();
    this.serverErrors();
    return {
      name: this.messageFor('name'),
      board: this.messageFor('board'),
      affiliationNumber: this.messageFor('affiliationNumber'),
      addressLine1: this.messageFor('addressLine1'),
      addressLine2: this.messageFor('addressLine2'),
      city: this.messageFor('city'),
      state: this.messageFor('state'),
      pincode: this.messageFor('pincode'),
      principalName: this.messageFor('principalName'),
      phone: this.messageFor('phone'),
      email: this.messageFor('email'),
      website: this.messageFor('website'),
    };
  });

  protected readonly failure = computed(() => {
    switch (this.failureCode()) {
      case null:
        return null;
      case 'SCHOOL_002':
        return {
          title: 'The school code cannot be changed',
          detail:
            'The code and the schema name identify this school. Ask Chalkbase support if one is wrong.',
        };
      case 'VAL_001':
        return {
          title: 'Some of these details were refused',
          detail: 'The fields marked below need correcting.',
        };
      case 'PERM_001':
        return {
          title: 'You do not have permission to edit the school profile',
          detail: 'Ask your principal to add "Edit the school profile" to your role.',
        };
      default:
        return {
          title: 'Could not save the profile',
          detail: 'Nothing was changed. Try again in a moment.',
        };
    }
  });

  constructor() {
    this.load();

    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.savedJustNow.set(false);
      if (Object.keys(this.serverErrors()).length > 0) {
        this.serverErrors.set({});
      }
    });

    // The router guard covers going somewhere else inside the app; only the browser can cover
    // leaving it. The wording is the browser's — every engine ignores a custom message — so this
    // is about the prompt appearing at all, not about what it says.
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      if (this.hasUnsavedChanges()) {
        event.preventDefault();
      }
    };
    window.addEventListener('beforeunload', warnBeforeUnload);
    this.destroyRef.onDestroy(() => window.removeEventListener('beforeunload', warnBeforeUnload));
  }

  /** Read by the router's `unsavedChangesGuard`. Saving is not "unsaved" — it is in flight. */
  hasUnsavedChanges(): boolean {
    return this.form.dirty && !this.saving();
  }

  protected reload(): void {
    this.load();
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }

    this.attempted.set(true);
    this.form.markAllAsTouched();

    if (this.form.invalid) {
      this.focusFirstInvalid();
      return;
    }

    const value = this.form.getRawValue();
    this.failureCode.set(null);
    this.serverErrors.set({});
    this.saving.set(true);
    // Read-only inputs alone are not enough: disabling the group is what stops a second Enter
    // starting another request while this one is in flight.
    this.form.disable({ emitEvent: false });

    this.schoolApi
      .updateProfile({
        code: value.code,
        schemaName: this.schemaName,
        name: value.name,
        board: value.board as Board,
        addressLine1: value.addressLine1,
        addressLine2: value.addressLine2,
        city: value.city,
        state: value.state,
        pincode: value.pincode,
        principalName: value.principalName,
        phone: value.phone,
        email: value.email,
        website: value.website,
        affiliationNumber: value.affiliationNumber,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (profile) => {
          this.finishSaving();
          this.fill(profile);
          this.attempted.set(false);
          this.savedJustNow.set(true);
        },
        error: (error: unknown) => {
          this.finishSaving();
          this.failureCode.set(apiErrorCode(error));
          this.serverErrors.set(this.fieldErrorsFrom(error));
        },
      });
  }

  /** Puts back what was last loaded or saved. The unsaved-changes bar goes with it. */
  protected cancel(): void {
    if (this.saving()) {
      return;
    }
    this.serverErrors.set({});
    this.failureCode.set(null);
    this.attempted.set(false);
    this.restore();
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);

    this.schoolApi
      .profile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (profile) => {
          this.fill(profile);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.loadFailed.set(true);
        },
      });
  }

  private fill(profile: SchoolProfileModel): void {
    this.schemaName = profile.schemaName;
    this.configured.set(profile.configured);
    this.saved = {
      code: profile.code,
      name: profile.name,
      board: profile.board,
      affiliationNumber: profile.affiliationNumber ?? '',
      addressLine1: profile.addressLine1 ?? '',
      addressLine2: profile.addressLine2 ?? '',
      city: profile.city ?? '',
      state: profile.state ?? '',
      pincode: profile.pincode ?? '',
      principalName: profile.principalName ?? '',
      phone: profile.phone ?? '',
      email: profile.email ?? '',
      website: profile.website ?? '',
    };
    this.restore();
  }

  private restore(): void {
    this.form.reset(this.saved, { emitEvent: false });
    // `reset` re-enables the group, and the code control must never be editable.
    this.form.controls.code.disable({ emitEvent: false });
    this.form.markAsPristine();
    this.form.markAsUntouched();
  }

  private finishSaving(): void {
    this.saving.set(false);
    this.form.enable({ emitEvent: false });
    this.form.controls.code.disable({ emitEvent: false });
  }

  /**
   * The server's per-field messages, keyed by the fields they name.
   *
   * The backend answers a validation failure with `details` keyed by field (ADR-0007), and those
   * keys are these control names — which is why the request record and this form use the same
   * names. A key this form does not have is dropped rather than shown with nothing to point at.
   */
  private fieldErrorsFrom(error: unknown): Readonly<Record<string, string>> {
    const known: Record<string, string> = {};
    for (const [field, message] of Object.entries(apiErrorDetails(error))) {
      if (Object.prototype.hasOwnProperty.call(this.form.controls, field)) {
        known[field] = message;
      }
    }
    return known;
  }

  private messageFor(name: FieldName): string | null {
    const fromServer = this.serverErrors()[name];
    if (fromServer) {
      return fromServer;
    }
    const control = this.form.controls[name];
    if (control.valid || !(control.touched || this.attempted())) {
      return null;
    }
    if (control.hasError('required')) {
      return REQUIRED[name] ?? 'This is required.';
    }
    if (control.hasError('email')) {
      return 'Enter an e-mail address, for example office@school.example.';
    }
    if (control.hasError('pattern')) {
      return MALFORMED[name] ?? 'That is not in the expected format.';
    }
    if (control.hasError('maxlength')) {
      return 'That is longer than this field allows.';
    }
    return 'Check this field.';
  }

  /**
   * Focus the first field that needs fixing, by id rather than by looking for the error styling:
   * the styling appears on the next render, and this runs before it.
   */
  private focusFirstInvalid(): void {
    const first = FIELD_ORDER.find((name) => this.form.controls[name].invalid);
    if (!first) {
      return;
    }
    this.host.nativeElement.querySelector<HTMLElement>(`#${FIELD_IDS[first]}`)?.focus();
  }
}
