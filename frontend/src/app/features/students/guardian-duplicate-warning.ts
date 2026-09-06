import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs';
import { GuardiansApi } from '../../core/api/guardians-api';
import { GuardianSummary } from '../../core/api/models';
import { Button } from '../../shared/components/button/button';

/** The same pause as every other search box on these screens. */
const DEBOUNCE_MS = 300;

/**
 * How much of a number has to be typed before this says anything.
 *
 * Six is a judgement, and the reasoning is worth stating because both extremes are worse. Check
 * from the first digit and every clerk typing `9` is told they are about to duplicate somebody, so
 * within a week nobody reads the box. Wait for all ten and the warning arrives after the number is
 * finished, which is still useful but later than it needs to be — and it never fires at all for a
 * school that records eight-digit landlines. Six digits is enough of a number to be a claim about
 * one person rather than about a prefix, and it is reached while the field is still being filled.
 */
const MIN_DIGITS = 6;

/** At most this many named, so the notice stays a notice rather than becoming a second list. */
const MAX_SHOWN = 3;

/** One possible duplicate, with everything the template needs already decided. */
interface Match {
  readonly guardian: GuardianSummary;
  readonly fullName: string;
  readonly phone: string | null;
  readonly linkedTo: string;
  readonly useButtonId: string;
}

/**
 * "Someone here already has this number" — said while a new guardian is being typed in.
 *
 * ## Why this warns and does not refuse
 *
 * Two people genuinely share a phone number. A couple most obviously, and a household where one
 * handset answers for a grandmother and an uncle as well; a school where the office number stands in
 * for a boarder's parents overseas is another. So a unique constraint on the number would refuse
 * real families, and an "I know, create anyway" checkbox would be a thing people tick without
 * reading — three clicks in, it is furniture. Neither is a control; both are a way of feeling like
 * one.
 *
 * What is honest is this: at the moment the duplicate would be created, say who is already here,
 * how many children they have, and offer the one action that avoids the mistake. The clerk who is
 * recording a mother with the father's number reads it, sees it is not the same person, and carries
 * on — which is exactly right, because it is not the same person. **Nothing here disables a button
 * or blocks a submit.** If this component fails to load, or the caller has no permission to search,
 * the form behaves as though it were not here.
 *
 * ## Why it checks the number rather than the name
 *
 * Because the name is already searched and the number is what the search used to miss. Two records
 * for one man are usually spelled two ways — "Suresh Kulkarni" and "S. Kulkarni" — so a name check
 * finds the duplicates that were never going to be created. The number is the discriminator, which
 * is why the backend search now compares digits to digits; this is the same comparison run one field
 * earlier.
 *
 * The server's `?q=` covers name and email as well, so a result is confirmed here against the digits
 * of its own phone before it is called a match. Otherwise a guardian whose *email* happened to
 * contain the digits would be reported as having the same number, which is a false accusation the
 * clerk has no way to check.
 *
 * ## Confidential (ADR-0014)
 *
 * A guardian's name and phone number. Nothing here is logged, and the number reaches the server only
 * as the search text the user themselves typed.
 */
@Component({
  selector: 'cb-guardian-duplicate-warning',
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './guardian-duplicate-warning.html',
  styleUrl: './guardian-duplicate-warning.scss',
})
export class GuardianDuplicateWarning {
  /** The phone number as it is being typed. Raw — this strips it. */
  readonly phone = input<string>('');

  /**
   * Guardians this screen must not offer, whatever their number.
   *
   * Two cases, and both would otherwise produce a suggestion that cannot be taken. On a student's
   * record it is the guardians already attached to that child — the link would be refused by
   * `uq_student_guardian_pair`, and the list above already says they are here. On the directory it
   * would be the record being edited, so that correcting a phone number does not warn about its own
   * owner.
   */
  readonly excludeGuardianIds = input<readonly string[]>([]);

  /** "Use this one instead." The caller decides what that means on its screen. */
  readonly use = output<GuardianSummary>();

  private readonly guardians = inject(GuardiansApi);
  private readonly destroyRef = inject(DestroyRef);

  private readonly found = signal<readonly GuardianSummary[]>([]);

  protected readonly matches = computed<readonly Match[]>(() =>
    this.found()
      .slice(0, MAX_SHOWN)
      .map((guardian) => ({
        guardian,
        fullName: guardian.fullName,
        phone: guardian.phone?.trim() || null,
        linkedTo: describeLinkCount(guardian.linkedStudentCount),
        useButtonId: `guardian-duplicate-use-${guardian.id}`,
      })),
  );

  /** "and 2 more" — so a long tail is admitted rather than silently cut. */
  protected readonly moreThanShown = computed(() => {
    const hidden = this.found().length - MAX_SHOWN;
    return hidden > 0 ? `…and ${hidden} more with this number.` : null;
  });

  protected readonly heading = computed(() =>
    this.found().length === 1
      ? 'Someone at this school already has this number'
      : 'Several guardians here already have this number',
  );

  constructor() {
    toObservable(this.phone)
      .pipe(
        map((typed) => digitsOf(typed)),
        debounceTime(DEBOUNCE_MS),
        distinctUntilChanged(),
        switchMap((digits) => {
          if (digits.length < MIN_DIGITS) {
            return of<readonly GuardianSummary[]>([]);
          }
          return this.guardians.search({ q: digits, size: 10 }).pipe(
            map((page) =>
              page.content.filter(
                (guardian) =>
                  !this.excludeGuardianIds().includes(guardian.id) &&
                  digitsOf(guardian.phone ?? '').includes(digits),
              ),
            ),
            // A failed check is silence, never a red box. This is an aid to a form that works
            // without it, and a search that did not run has established nothing to warn about.
            catchError(() => of<readonly GuardianSummary[]>([])),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((found) => this.found.set(found));
  }

  protected choose(match: Match): void {
    this.use.emit(match.guardian);
  }
}

/** Everything that is not 0-9, removed — the rule the database applies to the stored number. */
function digitsOf(text: string): string {
  return (text ?? '').replace(/[^0-9]/g, '');
}

/** "Linked to 4 students". The number is what says this record reaches beyond one child. */
function describeLinkCount(count: number): string {
  if (!count || count < 1) {
    return 'Not linked to any student yet';
  }
  return count === 1 ? 'Linked to 1 student' : `Linked to ${count} students`;
}
