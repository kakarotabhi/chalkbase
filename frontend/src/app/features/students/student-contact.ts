import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { apiErrorCode } from '../../core/api/api-error';
import { ContactDetail } from '../../core/api/models';
import { StudentsApi } from '../../core/api/students-api';
import { Permissions } from '../../core/auth/permissions';
import { permitted } from '../../core/auth/session-store';
import { Button } from '../../shared/components/button/button';
import { FormField } from '../../shared/components/form-field/form-field';
import { TextInput } from '../../shared/components/text-input/text-input';
import { ACCESS_DENIED } from './students-shared';

/** The backend's own limits, restated so a value is refused before the round trip rather than after. */
const PHONE_MAX_LENGTH = 20;
const EMAIL_MAX_LENGTH = 320;

/**
 * A student's own address, phone and email (FR-028).
 *
 * Confidential, not masked (ADR-0014) — the same tier as a guardian's own contact details, so this
 * section is a plain edit-in-place, the same shape as the "Details" card above it, with no reveal
 * step. Gated on `student:student:manage`, the permission that covers correcting any part of the
 * core record.
 */
@Component({
  selector: 'cb-student-contact',
  imports: [ReactiveFormsModule, Button, FormField, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-contact.html',
  styleUrl: './student-contact.scss',
})
export class StudentContact {
  readonly studentId = input.required<string>();
  readonly contact = input<ContactDetail | null>(null);

  readonly changed = output<string>();

  private readonly students = inject(StudentsApi);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly canManage = permitted(Permissions.STUDENT_MANAGE);

  protected readonly editing = signal(false);
  protected readonly busy = signal(false);
  protected readonly failureCode = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    address: '',
    phone: ['', Validators.maxLength(PHONE_MAX_LENGTH)],
    email: ['', [Validators.email, Validators.maxLength(EMAIL_MAX_LENGTH)]],
  });

  /** True once Save has been pressed: before that, only touched fields show a message. */
  private readonly attempted = signal(false);
  /** Bumped on every change so the messages recompute; values come off the controls. */
  private readonly revision = signal(0);

  protected readonly fieldErrors = computed<
    Readonly<{ phone: string | null; email: string | null }>
  >(() => {
    this.revision();
    this.attempted();
    return {
      phone: this.messageFor(this.form.controls.phone, 'phone'),
      email: this.messageFor(this.form.controls.email, 'email'),
    };
  });

  protected readonly view = computed(() => {
    const contact = this.contact();
    return {
      address: contact?.address?.trim() || null,
      phone: contact?.phone?.trim() || null,
      email: contact?.email?.trim() || null,
    };
  });

  protected readonly failure = computed(() => {
    switch (this.failureCode()) {
      case null:
        return null;
      case ACCESS_DENIED:
        return {
          title: 'You do not have permission to change this section',
          detail: 'Ask your principal to add "Manage students" to your role.',
        };
      default:
        return {
          title: 'Could not save these details',
          detail: 'Nothing was changed. Check your connection and try again.',
        };
    }
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.revision.update((count) => count + 1);
    });
  }

  protected startEdit(): void {
    this.failureCode.set(null);
    this.attempted.set(false);
    const contact = this.contact();
    this.form.reset(
      {
        address: contact?.address ?? '',
        phone: contact?.phone ?? '',
        email: contact?.email ?? '',
      },
      { emitEvent: false },
    );
    this.editing.set(true);
    this.focusAfterRender('#contact-address');
  }

  protected cancel(): void {
    if (this.busy()) {
      return;
    }
    this.editing.set(false);
    this.focusAfterRender('#contact-edit');
  }

  protected save(): void {
    if (this.busy()) {
      return;
    }
    this.attempted.set(true);
    this.form.markAllAsTouched();
    this.revision.update((count) => count + 1);
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.failureCode.set(null);
    const { address, phone, email } = this.form.getRawValue();

    this.students
      .saveContact(this.studentId(), {
        address: address.trim() || undefined,
        phone: phone.trim() || undefined,
        email: email.trim() || undefined,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.editing.set(false);
          this.changed.emit('Contact details were saved.');
          this.focusAfterRender('#contact-edit');
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }

  private messageFor(control: AbstractControl, name: 'phone' | 'email'): string | null {
    if (!control.touched && !this.attempted()) {
      return null;
    }
    if (name === 'email' && control.hasError('email')) {
      return 'Enter an email address like name@example.com.';
    }
    if (control.hasError('maxlength')) {
      return name === 'email'
        ? `An email address is ${EMAIL_MAX_LENGTH} characters or fewer.`
        : `A phone number is ${PHONE_MAX_LENGTH} characters or fewer.`;
    }
    return null;
  }
}
