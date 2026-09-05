import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormField } from './form-field';

@Component({
  imports: [FormField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cb-form-field
      #field
      label="School code"
      controlId="school-code"
      [required]="required()"
      [hint]="hint()"
      [error]="error()"
    >
      <input id="school-code" [attr.aria-describedby]="field.describedById()" />
    </cb-form-field>
  `,
})
class FormFieldHost {
  readonly required = signal(true);
  readonly hint = signal<string | null>('Shown on receipts and certificates.');
  readonly error = signal<string | null>(null);
}

describe('FormField', () => {
  let fixture: ComponentFixture<FormFieldHost>;
  let host: FormFieldHost;

  const text = () => fixture.nativeElement.textContent as string;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [FormFieldHost] }).compileComponents();
    fixture = TestBed.createComponent(FormFieldHost);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('ties the label to the control it wraps', () => {
    const label = fixture.nativeElement.querySelector('label') as HTMLLabelElement;
    expect(label.getAttribute('for')).toBe('school-code');
    expect(label.textContent).toContain('School code');
  });

  it('announces the required marker in words, not only as an asterisk', () => {
    expect(fixture.nativeElement.querySelector('.field__marker')?.textContent).toContain('*');
    expect(text()).toContain('(required)');

    host.required.set(false);
    fixture.detectChanges();

    expect(text()).not.toContain('(required)');
  });

  it('describes the control by its hint while the field is fine', () => {
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(text()).toContain('Shown on receipts and certificates.');
    expect(input.getAttribute('aria-describedby')).toBe('school-code-hint');
  });

  it('replaces the hint with the error rather than stacking the two', () => {
    host.error.set('A school with this code already exists');
    fixture.detectChanges();

    expect(text()).toContain('A school with this code already exists');
    expect(text()).not.toContain('Shown on receipts and certificates.');

    const error = fixture.nativeElement.querySelector('.field__error') as HTMLElement;
    expect(error.getAttribute('role')).toBe('alert');
    expect(error.id).toBe('school-code-error');

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.getAttribute('aria-describedby')).toBe('school-code-error');
  });

  it('describes nothing when there is neither a hint nor an error', () => {
    host.hint.set(null);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.getAttribute('aria-describedby')).toBeNull();
    expect(fixture.nativeElement.querySelector('.field__hint')).toBeNull();
  });
});
