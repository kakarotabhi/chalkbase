import {
  ChangeDetectionStrategy,
  Component,
  forwardRef,
  input,
  output,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/** One choice in a `cb-select`. `value` is what the form holds; `label` is what a user reads. */
export interface SelectOption {
  readonly value: string;
  readonly label: string;
}

/**
 * A single-choice control for reactive forms, built on the native `<select>`.
 *
 * Native on purpose. A hand-rolled listbox has to reimplement type-ahead, the escape key, the
 * scroll-into-view of the selected option and — the part nobody gets right — the way Android and
 * iOS replace the dropdown with a full-screen picker that is far easier to hit with a thumb than
 * any menu we could draw. The chevron is ours, the box is ours, the behaviour is the platform's.
 *
 * Presentational only, like `cb-text-input`: the label, hint and error live in `cb-form-field`,
 * which also mints the `id` and `describedBy` this takes.
 */
@Component({
  selector: 'cb-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './select.html',
  styleUrl: './select.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => Select),
      multi: true,
    },
  ],
})
export class Select implements ControlValueAccessor {
  readonly options = input.required<readonly SelectOption[]>();
  readonly id = input<string | null>(null);
  /**
   * The empty first entry, e.g. "Choose a board". Shown only while nothing is selected and never
   * selectable afterwards — an empty choice a user can return to is a way to clear a required
   * field without being told why the form then refuses.
   */
  readonly placeholder = input<string | null>(null);
  readonly describedBy = input<string | null>(null);
  readonly invalid = input(false);
  /**
   * The "request in flight" state. A native select has no read-only, so this disables it — and the
   * form is expected to disable the whole group while saving, which is what keeps the appearance
   * consistent with the read-only text inputs beside it.
   */
  readonly readOnly = input(false);

  readonly blurred = output<void>();

  protected readonly value = signal('');
  protected readonly isDisabled = signal(false);

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.isDisabled.set(isDisabled);
  }

  protected handleChange(event: Event): void {
    const next = (event.target as HTMLSelectElement).value;
    this.value.set(next);
    this.onChange(next);
  }

  protected handleBlur(): void {
    this.onTouched();
    this.blurred.emit();
  }
}
