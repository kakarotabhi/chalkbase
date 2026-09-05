import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Button } from './button';

@Component({
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cb-button
      [variant]="variant()"
      [loading]="loading()"
      [disabled]="disabled()"
      [block]="block()"
      [loadingLabel]="loadingLabel()"
      (click)="clicks.set(clicks() + 1)"
    >
      Save changes
    </cb-button>
  `,
})
class ButtonHost {
  readonly variant = signal<'primary' | 'secondary' | 'danger' | 'ghost'>('primary');
  readonly loading = signal(false);
  readonly disabled = signal(false);
  readonly block = signal(false);
  readonly loadingLabel = signal('');
  readonly clicks = signal(0);
}

describe('Button', () => {
  let fixture: ComponentFixture<ButtonHost>;
  let host: ButtonHost;

  const button = () => fixture.nativeElement.querySelector('button') as HTMLButtonElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ButtonHost] }).compileComponents();
    fixture = TestBed.createComponent(ButtonHost);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders its projected label on an enabled button', () => {
    expect(button().textContent).toContain('Save changes');
    expect(button().disabled).toBe(false);
    expect(button().getAttribute('aria-busy')).toBeNull();
  });

  it('carries the variant so each one is styled apart', () => {
    expect(button().className).toContain('btn--primary');

    host.variant.set('danger');
    fixture.detectChanges();

    expect(button().className).toContain('btn--danger');
    expect(button().className).not.toContain('btn--primary');
  });

  it('shows progress, keeps the label in place for width, and blocks a second click', () => {
    host.loading.set(true);
    host.loadingLabel.set('Saving…');
    fixture.detectChanges();

    expect(button().disabled).toBe(true);
    expect(button().getAttribute('aria-busy')).toBe('true');
    expect(button().textContent).toContain('Saving…');
    // The idle label stays in flow so the button cannot change width mid-request.
    expect(fixture.nativeElement.querySelector('.btn__label.is-hidden')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.btn__label')?.textContent).toContain(
      'Save changes',
    );

    button().click();
    expect(host.clicks()).toBe(0);
  });

  it('stays visible but unusable when disabled', () => {
    host.disabled.set(true);
    fixture.detectChanges();

    expect(button()).not.toBeNull();
    expect(button().disabled).toBe(true);
    expect(button().textContent).toContain('Save changes');

    button().click();
    expect(host.clicks()).toBe(0);
  });

  it('goes full width on request', () => {
    expect(button().className).not.toContain('btn--block');

    host.block.set(true);
    fixture.detectChanges();

    expect(button().className).toContain('btn--block');
  });

  it('is clickable by keyboard when idle', () => {
    button().click();
    expect(host.clicks()).toBe(1);
  });
});
