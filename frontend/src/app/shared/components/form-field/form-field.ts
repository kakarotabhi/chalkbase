import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

let sequence = 0;

/**
 * Label, control and message for one form control.
 *
 * The control is projected rather than owned, so this works for an input, a password field, a
 * select or anything else added later. It hands back the ids it minted — `controlId()` and
 * `describedById()` — for the projected control to bind, which is what actually ties the label to
 * the input and makes the error part of the control's accessible description:
 *
 * ```html
 * <cb-form-field #code label="School code" [required]="true" [error]="codeError()">
 *   <cb-text-input
 *     formControlName="schoolCode"
 *     [id]="code.controlId()"
 *     [describedBy]="code.describedById()"
 *     [invalid]="code.hasError()"
 *   />
 * </cb-form-field>
 * ```
 *
 * An error replaces the hint instead of stacking below it. Two messages under one field is how you
 * get a user reading the wrong one.
 */
@Component({
  selector: 'cb-form-field',
  exportAs: 'cbFormField',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './form-field.html',
  styleUrl: './form-field.scss',
})
export class FormField {
  readonly label = input.required<string>();
  /** Defaults to a unique id, so the common case needs no id bookkeeping in the feature. */
  readonly controlId = input(`cb-field-${++sequence}`);
  readonly required = input(false);
  readonly hint = input<string | null>(null);
  /** The message to show instead of the hint. Null means the field is fine. */
  readonly error = input<string | null>(null);

  readonly hasError = computed(() => this.error() !== null && this.error() !== '');

  protected readonly errorId = computed(() => `${this.controlId()}-error`);
  protected readonly hintId = computed(() => `${this.controlId()}-hint`);

  /** What the projected control should point `aria-describedby` at, or null if there is nothing. */
  readonly describedById = computed(() => {
    if (this.hasError()) {
      return this.errorId();
    }
    return this.hint() ? this.hintId() : null;
  });
}
