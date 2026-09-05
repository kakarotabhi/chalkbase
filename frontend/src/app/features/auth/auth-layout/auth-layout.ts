import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * The frame both sign-in screens sit in.
 *
 * One arrangement of one set of markup: on a phone the brand sits above the form and the panel
 * copy is not rendered at all; from `expanded` up the same brand moves into a primary-coloured
 * column beside the form and the copy appears with it. The heading and the form itself are
 * identical in both — only the surroundings change.
 */
@Component({
  selector: 'cb-auth-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './auth-layout.html',
  styleUrl: './auth-layout.scss',
})
export class AuthLayout {
  readonly heading = input.required<string>();
  readonly subtitle = input<string | null>(null);
}
