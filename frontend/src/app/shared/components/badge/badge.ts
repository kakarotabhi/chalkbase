import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * A status pill's tone. Each pairs a tinted surface with its own foreground (`_tokens.scss`),
 * except `neutral`, which borders instead of tinting — for a state that is not good, bad, due or
 * informational, just ended (a past academic session, a graduated student).
 */
export type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'primary' | 'neutral';

/**
 * One status word or short phrase, pill-shaped, coloured by tone.
 *
 * This is the sixth hand-rolled copy of the same three declarations (`background`, `color`,
 * `border-radius`) becoming a component instead of a seventh: `student-list` and `student-detail`
 * had verbatim-copied `.status`, `student-documents` and `user-roster` each had their own, and
 * `academic-sessions`, `student-enrolments` and `student-guardians` had a second shape for the
 * same idea under the name `.badge`. All of them used `--cb-radius-lg` (12px) where the approved
 * design is a full pill — a consistent mistake, because it was copied. Fixed once, here.
 *
 * Colour is never the only signal in this codebase (`_tokens.scss`): the tone is a visual
 * reinforcement of words already in the projected content, not a replacement for them, so there is
 * no icon-only mode.
 */
@Component({
  selector: 'cb-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './badge.html',
  styleUrl: './badge.scss',
  host: {
    class: 'cb-badge',
    '[class.cb-badge--success]': "tone() === 'success'",
    '[class.cb-badge--warning]': "tone() === 'warning'",
    '[class.cb-badge--danger]': "tone() === 'danger'",
    '[class.cb-badge--info]': "tone() === 'info'",
    '[class.cb-badge--primary]': "tone() === 'primary'",
    '[class.cb-badge--neutral]': "tone() === 'neutral'",
  },
})
export class Badge {
  readonly tone = input<BadgeTone>('neutral');
}
