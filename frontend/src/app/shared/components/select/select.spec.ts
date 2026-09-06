import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Select, SelectOption } from './select';

const BOARDS: readonly SelectOption[] = [
  { value: 'CBSE', label: 'CBSE' },
  { value: 'CISCE', label: 'CISCE (ICSE / ISC)' },
  { value: 'STATE', label: 'State board' },
];

@Component({
  imports: [ReactiveFormsModule, Select],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cb-select
      [formControl]="control"
      id="board"
      [options]="boards"
      placeholder="Choose a board"
      [invalid]="invalid()"
      [readOnly]="readOnly()"
      describedBy="board-hint"
    />
  `,
})
class SelectHost {
  readonly control = new FormControl('', { nonNullable: true });
  readonly boards = BOARDS;
  readonly invalid = signal(false);
  readonly readOnly = signal(false);
}

/** The filter-bar shape: no visible label, no placeholder, and a real "not filtering" option. */
@Component({
  imports: [ReactiveFormsModule, Select],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cb-select
      variant="pill"
      ariaLabel="Board"
      [formControl]="control"
      id="board-filter"
      [options]="boards"
    />
  `,
})
class PillHost {
  readonly control = new FormControl('', { nonNullable: true });
  readonly boards: readonly SelectOption[] = [{ value: '', label: 'Any board' }, ...BOARDS];
}

describe('Select', () => {
  let fixture: ComponentFixture<SelectHost>;
  let host: SelectHost;

  const select = () => fixture.nativeElement.querySelector('select') as HTMLSelectElement;
  const options = () => Array.from(select().querySelectorAll('option'));

  const choose = (value: string) => {
    select().value = value;
    select().dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SelectHost] }).compileComponents();
    fixture = TestBed.createComponent(SelectHost);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders every option it was given, behind the placeholder', () => {
    expect(options().map((option) => option.textContent?.trim())).toEqual([
      'Choose a board',
      'CBSE',
      'CISCE (ICSE / ISC)',
      'State board',
    ]);
    expect(select().id).toBe('board');
    expect(select().getAttribute('aria-describedby')).toBe('board-hint');
  });

  /** Otherwise a user can clear a required field and be told only on submit. */
  it('will not let the placeholder be chosen again', () => {
    expect(options()[0].disabled).toBe(true);
  });

  it('writes the chosen value back to the form control', () => {
    choose('CISCE');
    expect(host.control.value).toBe('CISCE');
  });

  it('shows a value the form puts in', () => {
    host.control.setValue('STATE');
    fixture.detectChanges();
    expect(select().value).toBe('STATE');
  });

  it('marks the control touched when it loses focus', () => {
    select().dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(host.control.touched).toBe(true);
  });

  it('flags itself invalid for assistive technology', () => {
    host.invalid.set(true);
    fixture.detectChanges();

    expect(select().getAttribute('aria-invalid')).toBe('true');
    expect(select().className).toContain('select--invalid');
  });

  /** A native select has no read-only, so the in-flight state has to disable it. */
  it('stops accepting a choice while a request is in flight', () => {
    host.readOnly.set(true);
    fixture.detectChanges();

    expect(select().disabled).toBe(true);
  });

  it('disables itself when the form disables the control', () => {
    host.control.disable();
    fixture.detectChanges();

    expect(select().disabled).toBe(true);
  });

  /**
   * The pill variant, which the student list's filter bar is built from.
   *
   * What is worth proving here is the pair of things the shape is for and the one thing it must not
   * cost: it prints the value it holds, it says when that value is narrowing anything, and it still
   * has a name a screen reader can read out even though nothing on screen is labelling it.
   */
  describe('as a pill', () => {
    let pill: ComponentFixture<PillHost>;

    const control = () => pill.nativeElement.querySelector('select') as HTMLSelectElement;
    const sizer = () => pill.nativeElement.querySelector('.select__sizer') as HTMLElement;

    // No second `configureTestingModule`: the outer one has already instantiated the module, and
    // a standalone component needs nothing declared for it anyway.
    beforeEach(() => {
      pill = TestBed.createComponent(PillHost);
      pill.detectChanges();
    });

    /**
     * The visible text is the value, so it cannot also be the name. Without this the control is
     * announced as a combo box called "Any board", which says what is chosen and never what
     * choosing it does.
     */
    it('carries a name of its own, because nothing on screen labels it', () => {
      expect(control().getAttribute('aria-label')).toBe('Board');
    });

    /**
     * The sizer is what makes a pill as wide as the option it holds rather than as wide as the
     * longest one it offers, so it has to track the value — and it must stay out of the
     * accessibility tree, or the label is announced twice.
     */
    it('prints the option it is holding, and hides that copy from assistive technology', () => {
      expect(sizer().textContent?.trim()).toBe('Any board');
      expect(sizer().getAttribute('aria-hidden')).toBe('true');

      pill.componentInstance.control.setValue('STATE');
      pill.detectChanges();

      expect(sizer().textContent?.trim()).toBe('State board');
    });

    /** "Is this filter doing anything" is the one question a filter bar answers at a glance. */
    it('tints itself once it is narrowing something, and not before', () => {
      expect(control().className).not.toContain('select--active');

      pill.componentInstance.control.setValue('CBSE');
      pill.detectChanges();
      expect(control().className).toContain('select--active');

      // The empty string is the "Any board" option, which is a real choice and not a placeholder.
      pill.componentInstance.control.setValue('');
      pill.detectChanges();
      expect(control().className).not.toContain('select--active');
    });

    /** Whatever the skin, it is the same native select underneath. */
    it("is still the platform's own control", () => {
      control().value = 'CISCE';
      control().dispatchEvent(new Event('change'));
      pill.detectChanges();

      expect(pill.componentInstance.control.value).toBe('CISCE');
    });
  });
});
