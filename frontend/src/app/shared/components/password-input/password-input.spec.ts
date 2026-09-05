import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { PasswordInput } from './password-input';

@Component({
  imports: [ReactiveFormsModule, PasswordInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<cb-password-input [formControl]="control" id="password" />`,
})
class PasswordInputHost {
  readonly control = new FormControl('', { nonNullable: true });
}

describe('PasswordInput', () => {
  let fixture: ComponentFixture<PasswordInputHost>;
  let host: PasswordInputHost;

  const input = () => fixture.nativeElement.querySelector('input') as HTMLInputElement;
  const toggle = () =>
    fixture.nativeElement.querySelector('.password__toggle') as HTMLButtonElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PasswordInputHost] }).compileComponents();
    fixture = TestBed.createComponent(PasswordInputHost);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('hides the password until asked', () => {
    expect(input().getAttribute('type')).toBe('password');
    expect(toggle().getAttribute('aria-label')).toBe('Show password');
    expect(toggle().getAttribute('aria-pressed')).toBe('false');
  });

  it('reveals and re-hides the password, saying so each time', () => {
    toggle().click();
    fixture.detectChanges();

    expect(input().getAttribute('type')).toBe('text');
    expect(toggle().getAttribute('aria-label')).toBe('Hide password');
    expect(toggle().getAttribute('aria-pressed')).toBe('true');

    toggle().click();
    fixture.detectChanges();

    expect(input().getAttribute('type')).toBe('password');
    expect(toggle().getAttribute('aria-pressed')).toBe('false');
  });

  it('keeps the toggle out of the way of a submit and reachable by keyboard', () => {
    // type="button" is what stops the reveal toggle submitting the form it sits in.
    expect(toggle().type).toBe('button');
    expect(toggle().tabIndex).toBe(0);
    expect(toggle().getAttribute('aria-controls')).toBe('password');
  });

  it('writes what the user types back to the form control', () => {
    input().value = 'chalk-and-talk-9!';
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(host.control.value).toBe('chalk-and-talk-9!');
  });

  it('marks the control touched when it loses focus', () => {
    input().dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(host.control.touched).toBe(true);
  });
});
