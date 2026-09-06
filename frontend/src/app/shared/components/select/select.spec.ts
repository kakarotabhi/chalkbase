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
});
