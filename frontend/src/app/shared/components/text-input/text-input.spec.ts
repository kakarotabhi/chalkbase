import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TextInput } from './text-input';

@Component({
  imports: [ReactiveFormsModule, TextInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cb-text-input
      [formControl]="control"
      id="username"
      [invalid]="invalid()"
      [readOnly]="readOnly()"
      describedBy="username-hint"
      autocomplete="username"
    />
  `,
})
class TextInputHost {
  readonly control = new FormControl('', { nonNullable: true });
  readonly invalid = signal(false);
  readonly readOnly = signal(false);
}

describe('TextInput', () => {
  let fixture: ComponentFixture<TextInputHost>;
  let host: TextInputHost;

  const input = () => fixture.nativeElement.querySelector('input') as HTMLInputElement;

  const type = (value: string) => {
    input().value = value;
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [TextInputHost] }).compileComponents();
    fixture = TestBed.createComponent(TextInputHost);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders an addressable input carrying the attributes it was given', () => {
    expect(input().id).toBe('username');
    expect(input().getAttribute('autocomplete')).toBe('username');
    expect(input().getAttribute('aria-describedby')).toBe('username-hint');
    expect(input().getAttribute('aria-invalid')).toBeNull();
  });

  it('writes what the user types back to the form control', () => {
    type('priya.sharma');
    expect(host.control.value).toBe('priya.sharma');
  });

  it('shows a value the form puts in', () => {
    host.control.setValue('nisha.rao');
    fixture.detectChanges();
    expect(input().value).toBe('nisha.rao');
  });

  it('marks the control touched when it loses focus', () => {
    expect(host.control.touched).toBe(false);

    input().dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(host.control.touched).toBe(true);
  });

  it('flags itself invalid for assistive technology', () => {
    host.invalid.set(true);
    fixture.detectChanges();

    expect(input().getAttribute('aria-invalid')).toBe('true');
    expect(input().className).toContain('input--invalid');
  });

  it('goes read-only rather than disappearing while a request is in flight', () => {
    host.readOnly.set(true);
    fixture.detectChanges();

    expect(input().readOnly).toBe(true);
    expect(input().disabled).toBe(false);
  });

  it('disables the input when the form disables the control', () => {
    host.control.disable();
    fixture.detectChanges();

    expect(input().disabled).toBe(true);
  });
});
