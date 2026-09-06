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
 * How a `cb-select` is drawn.
 *
 * - `field` is the default: a full-width box under a `cb-form-field` label, the shape every form
 *   on the site uses.
 * - `pill` is the filter-bar shape from the approved designs — it shrinks to the width of the
 *   option currently chosen, prints that option as its own text instead of a label above it, and
 *   tints itself once it is narrowing anything. It has no visible label, so `ariaLabel` is
 *   required to say what it selects.
 */
export type SelectVariant = 'field' | 'pill';

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
 *
 * ## The pill variant
 *
 * A filter bar reads better as a row of pills that each show the value they hold ("Class 5 · A")
 * than as a stack of label / control / hint, and the designs draw it that way. That is a skin on
 * this component rather than a fourth control, because everything underneath — the platform
 * picker, type-ahead, the escape key, keyboard focus — is the same select and must not be
 * reimplemented per screen. Six copies of a hand-rolled badge in this codebase, all wrong the same
 * way, is what a fourth variant looks like a year later.
 *
 * Two things the pill does that the field does not:
 *
 * - **It sizes to the chosen option, not the longest one.** A native `<select>` laid out at
 *   `width: auto` is as wide as its widest option, which for a list of every section in a school
 *   is most of the row. So a hidden text node holding just the selected label sits in the same
 *   grid cell and sets the width, and the select stretches to it.
 * - **It tints itself when it is narrowing the list.** "Is this filter doing anything" is the one
 *   question a filter bar has to answer at a glance, and the empty value is already the
 *   convention for "not filtering" here — every filter offers "Any status" or "Any class or
 *   section" as a real option rather than a placeholder, precisely so it can be cleared.
 */
@Component({
  selector: 'cb-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './select.html',
  styleUrl: './select.scss',
  host: { '[class.is-pill]': 'pill()' },
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
  readonly variant = input<SelectVariant>('field');
  /**
   * The accessible name, for a variant that shows no label.
   *
   * A pill's visible text is its current value — "All sections" — which says what is selected and
   * not what is being selected. Without this, a screen reader announces a combo box called "All
   * sections", so a pill is expected to pass one and the field variant, which has a real `<label>`
   * from `cb-form-field`, leaves it null.
   */
  readonly ariaLabel = input<string | null>(null);
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

  protected readonly pill = computed(() => this.variant() === 'pill');

  /**
   * Whether this pill is narrowing anything, which is what earns it the tinted treatment.
   *
   * The empty string is "no choice" throughout this app — the first option of every filter here is
   * a real, selectable "Any …" entry rather than the select's placeholder — so a pill holding
   * anything else is doing something.
   */
  protected readonly active = computed(() => this.pill() && this.value() !== '');

  /** What the pill prints, and what the hidden sizer beside it is measured on. */
  protected readonly selectedLabel = computed(
    () =>
      this.options().find((option) => option.value === this.value())?.label ??
      this.placeholder() ??
      '',
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
