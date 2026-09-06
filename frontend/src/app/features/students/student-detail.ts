import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { apiErrorCode, apiErrorDetails } from '../../core/api/api-error';
import { SaveStudentRequest, StudentDetail as StudentRecord } from '../../core/api/models';
import { StudentsApi } from '../../core/api/students-api';
import { Button } from '../../shared/components/button/button';
import { formatDay } from '../../shared/formatting/day';
import { StudentEnrolments } from './student-enrolments';
import { StudentForm } from './student-form';
import { StudentGuardians } from './student-guardians';
import {
  ACCESS_DENIED,
  GENDER_LABELS,
  NOT_FOUND,
  STATUS_LABELS,
  classAndSection,
  labelFor,
} from './students-shared';

/**
 * One student's record: who they are, who to ring, and where they have sat.
 *
 * ## The three parts are three components, and the record is read once
 *
 * `GET /api/students/{id}` answers with the student, their guardians and their enrolments in one
 * payload, so this screen makes one request and hands slices of the answer to two children. When
 * either of them writes something it says so and this re-reads the whole record rather than
 * patching a row — which is not tidiness: setting a new main contact clears the previous one
 * server-side, so a screen that patched only the row it asked about would show two main contacts
 * until something else refetched.
 *
 * ## Nothing here deletes a student
 *
 * ADR-0020 §6, and it is worth saying where somebody would look for the button: fees, attendance
 * and marks all point at a student, and the status field on the form is how a school records that
 * one has left. The one delete on this screen removes a **guardian link** and leaves the person in
 * place for their other children.
 *
 * ## The name is not in the page title, and not in the URL
 *
 * A child's name and date of birth are Confidential (ADR-0014). The route is `/students/<uuid>` and
 * the browser tab says "Student", because a title goes into the window manager, into history, and
 * into every screenshot anybody takes of this screen.
 *
 * ## There is no guard on this route, deliberately
 *
 * ADR-0008: the server hides the menu item and the endpoint enforces the permission. Typing the URL
 * without it lands here and gets a 403 this screen explains, and a 404 for an id that is not at
 * this school is explained too rather than being reported as a fault.
 */
@Component({
  selector: 'cb-student-detail',
  imports: [RouterLink, Button, StudentForm, StudentGuardians, StudentEnrolments],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './student-detail.html',
  styleUrl: './student-detail.scss',
})
export class StudentDetail {
  /** Bound from the route by `withComponentInputBinding`. A UUID, and nothing about the child. */
  readonly id = input.required<string>();

  private readonly students = inject(StudentsApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  protected readonly loading = signal(true);
  /** The `error.code` of the last failed load, or null. Never the message (ADR-0007). */
  protected readonly failureCode = signal<string | null>(null);
  protected readonly student = signal<StudentRecord | null>(null);

  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly saveFailureCode = signal<string | null>(null);
  protected readonly saveFieldErrors = signal<Readonly<Record<string, string>>>({});

  /** What just happened, said once, in one live region rather than a banner per action. */
  protected readonly announcement = signal('');

  protected readonly forbidden = computed(() => this.failureCode() === ACCESS_DENIED);
  protected readonly missing = computed(() => this.failureCode() === NOT_FOUND);
  protected readonly failed = computed(() => {
    const code = this.failureCode();
    return code !== null && code !== ACCESS_DENIED && code !== NOT_FOUND;
  });

  /** The header, with everything the template needs already decided. */
  protected readonly view = computed(() => {
    const student = this.student();
    if (!student) {
      return null;
    }
    // `?? []` because the list may be absent rather than empty — see `StudentDetail` in models.ts.
    const current = (student.enrolments ?? []).find((enrolment) => enrolment.active) ?? null;
    return {
      fullName: student.fullName,
      admissionNumber: student.admissionNumber,
      status: student.status,
      statusLabel: labelFor(STATUS_LABELS, student.status),
      statusClass: `status status--${student.status.toLowerCase()}`,
      gender: labelFor(GENDER_LABELS, student.gender),
      dateOfBirth: formatDay(student.dateOfBirth),
      admittedOn: student.admittedOn ? formatDay(student.admittedOn) : null,
      placement: current ? classAndSection(current.className, current.sectionName) : null,
      session: current?.sessionName ?? null,
      rollNumber: current?.rollNumber?.trim() || null,
    };
  });

  constructor() {
    // Re-reads when the route id changes, which happens when somebody follows a link from one
    // record to another without the component being torn down.
    effect(() => {
      const id = this.id();
      this.editing.set(false);
      this.announcement.set('');
      this.load(id);
    });
  }

  protected reload(): void {
    this.load(this.id());
  }

  // ── Editing the record ───────────────────────────────────────────────────────────────────

  protected startEdit(): void {
    this.saveFailureCode.set(null);
    this.saveFieldErrors.set({});
    this.announcement.set('');
    this.editing.set(true);
  }

  protected cancelEdit(): void {
    if (this.saving()) {
      return;
    }
    this.editing.set(false);
    this.focusAfterRender('#student-edit');
  }

  protected save(request: SaveStudentRequest): void {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.saveFailureCode.set(null);
    this.saveFieldErrors.set({});

    this.students
      .update(this.id(), request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (student) => {
          this.saving.set(false);
          this.editing.set(false);
          // `PUT` answers with the whole record, guardians and enrolments included, so this is a
          // complete replacement rather than a merge that could leave the two disagreeing.
          this.student.set(student);
          this.announcement.set('The record was saved.');
          this.focusAfterRender('#student-edit');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveFailureCode.set(apiErrorCode(error));
          this.saveFieldErrors.set(apiErrorDetails(error));
        },
      });
  }

  /**
   * A child wrote something. Re-read the record and say what happened.
   *
   * Deliberately a full re-read rather than a patch — see the class comment. A failure here is left
   * as it is: the write went through, so what is on screen is stale rather than wrong, and turning
   * a successful change into an error banner would be a lie.
   */
  protected onChildChanged(announcement: string): void {
    this.announcement.set(announcement);
    this.students
      .get(this.id())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (student) => this.student.set(student), error: () => {} });
  }

  // ── internals ────────────────────────────────────────────────────────────────────────────

  private load(id: string): void {
    this.loading.set(true);
    this.failureCode.set(null);

    this.students
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (student) => {
          this.student.set(student);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.student.set(null);
          this.loading.set(false);
          this.failureCode.set(apiErrorCode(error));
        },
      });
  }

  /** The app is zoneless, so the element does not exist until the next render. */
  private focusAfterRender(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
