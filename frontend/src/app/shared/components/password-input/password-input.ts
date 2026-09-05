import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  output,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * A password control with a reveal toggle.
 *
 * The toggle is a real `<button>` inside the field, 44px square, so it is reachable by keyboard and
 * hittable with a thumb. It reports state through `aria-pressed` and swaps its own label, which is
 * what lets a screen-reader user know the password is currently on screen — the eye icon alone
 * tells them nothing.
 */
@Component({
  selector: 'cb-password-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './password-input.html',
  styleUrl: './password-input.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => PasswordInput),
      multi: true,
    },
  ],
})
export class PasswordInput implements ControlValueAccessor {
  readonly id = input<string | null>(null);
  readonly placeholder = input('');
  /** `current-password` on sign-in, `new-password` on a change form. */
  readonly autocomplete = input<string | null>('current-password');
  readonly describedBy = input<string | null>(null);
  readonly invalid = input(false);
  readonly readOnly = input(false);

  readonly blurred = output<void>();
  readonly visibilityChanged = output<boolean>();

  protected readonly value = signal('');
  protected readonly isDisabled = signal(false);
  protected readonly revealed = signal(false);

  protected readonly toggleLabel = computed(() =>
    this.revealed() ? 'Hide password' : 'Show password',
  );

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

  protected toggleVisibility(): void {
    this.revealed.update((revealed) => !revealed);
    this.visibilityChanged.emit(this.revealed());
  }
}
