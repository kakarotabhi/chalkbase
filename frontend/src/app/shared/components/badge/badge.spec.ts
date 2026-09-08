import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Badge, BadgeTone } from './badge';

@Component({
  imports: [Badge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<cb-badge [tone]="tone()">{{ label() }}</cb-badge>`,
})
class BadgeHost {
  readonly tone = signal<BadgeTone>('neutral');
  readonly label = signal('Active');
}

describe('Badge', () => {
  let fixture: ComponentFixture<BadgeHost>;
  let host: BadgeHost;

  const badge = () => fixture.nativeElement.querySelector('cb-badge') as HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [BadgeHost] }).compileComponents();
    fixture = TestBed.createComponent(BadgeHost);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders its projected content', () => {
    expect(badge().textContent?.trim()).toBe('Active');
  });

  it('defaults to the neutral tone', () => {
    expect(badge().classList).toContain('cb-badge--neutral');
  });

  it('carries exactly one tone class at a time', () => {
    host.tone.set('success');
    fixture.detectChanges();

    expect(badge().classList).toContain('cb-badge--success');
    expect(badge().classList).not.toContain('cb-badge--neutral');

    host.tone.set('danger');
    fixture.detectChanges();

    expect(badge().classList).toContain('cb-badge--danger');
    expect(badge().classList).not.toContain('cb-badge--success');
  });

  it('never colours the only signal — the projected text still carries the state', () => {
    host.tone.set('warning');
    host.label.set('Overdue');
    fixture.detectChanges();

    expect(badge().textContent?.trim()).toBe('Overdue');
  });
});
