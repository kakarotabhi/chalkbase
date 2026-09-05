import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Checkbox } from './checkbox';

@Component({
  imports: [ReactiveFormsModule, Checkbox],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<cb-checkbox [formControl]="control" label="Keep me signed in" />`,
})
class CheckboxHost {
  readonly control = new FormControl(false, { nonNullable: true });
}

describe('Checkbox', () => {
  let fixture: ComponentFixture<CheckboxHost>;
  let host: CheckboxHost;

  const input = () => fixture.nativeElement.querySelector('input') as HTMLInputElement;
  const label = () => fixture.nativeElement.querySelector('label') as HTMLLabelElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CheckboxHost] }).compileComponents();
    fixture = TestBed.createComponent(CheckboxHost);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('is a real checkbox, labelled, and starts unchecked', () => {
    expect(input().type).toBe('checkbox');
    expect(input().checked).toBe(false);
    expect(label().textContent).toContain('Keep me signed in');
  });

  it('toggles from a click anywhere on the row, not just the box', () => {
    // Clicking the label is what a thumb landing on the words does; the native control follows.
    label().click();
    fixture.detectChanges();

    expect(input().checked).toBe(true);
    expect(host.control.value).toBe(true);
  });

  it('is operable from the keyboard through the native control', () => {
    // The input keeps its place in the tab order — it is painted over, not removed.
    expect(input().tabIndex).toBe(0);

    input().click(); // what space does to a focused checkbox
    fixture.detectChanges();

    expect(host.control.value).toBe(true);
  });

  it('shows a tick once checked', () => {
    expect(fixture.nativeElement.querySelector('.checkbox__box svg')).toBeNull();

    host.control.setValue(true);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.checkbox__box svg')).not.toBeNull();
  });

  it('marks the control touched when it loses focus', () => {
    input().dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(host.control.touched).toBe(true);
  });

  it('goes disabled with its form control', () => {
    host.control.disable();
    fixture.detectChanges();

    expect(input().disabled).toBe(true);
    expect(label().className).toContain('is-disabled');
  });
});
