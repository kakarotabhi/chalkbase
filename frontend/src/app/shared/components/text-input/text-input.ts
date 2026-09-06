import {
  ChangeDetectionStrategy,
  Component,
  forwardRef,
  input,
  output,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * A single-line text control for reactive forms.
 *
 * Presentational only: it knows nothing about which form it is in or what the value means. The
 * label, hint and error live in `cb-form-field`, which also mints the `id` and `describedBy` this
 * takes.
 */
@Component({
  selector: 'cb-text-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './text-input.html',
  styleUrl: './text-input.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TextInput),
      multi: true,
    },
  ],
})
export class TextInput implements ControlValueAccessor {
  readonly id = input<string | null>(null);
  /**
   * `date` uses the platform's own picker rather than a hand-built calendar, for the same reason
   * `cb-select` stays a native `<select>`: Android and iOS replace it with a full-screen picker
   * that is far easier to hit with a thumb than anything we could draw. Its value is a
   * `yyyy-MM-dd` string in the user's own calendar day, never an instant.
   */
  readonly type = input<'text' | 'email' | 'tel' | 'date'>('text');
  readonly placeholder = input('');
  readonly autocomplete = input<string | null>(null);
  readonly inputmode = input<string | null>(null);
  readonly autocapitalize = input<string | null>(null);
  readonly describedBy = input<string | null>(null);
  readonly invalid = input(false);
  readonly readOnly = input(false);

  /** Fires when the control loses focus, for screens that want to react beyond marking it touched. */
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

  protected handleInput(event: Event): void {
    const next = (event.target as HTMLInputElement).value;
    this.value.set(next);
    this.onChange(next);
  }

  protected handleBlur(): void {
    this.onTouched();
    this.blurred.emit();
  }
}
