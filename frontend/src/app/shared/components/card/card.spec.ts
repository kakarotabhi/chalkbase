import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Card, CardPadding, CardTone } from './card';

@Component({
  imports: [Card],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <cb-card [tone]="tone()" [padding]="padding()" [accent]="accent()">
      <p>Card content</p>
    </cb-card>
  `,
})
class CardHost {
  readonly tone = signal<CardTone>('raised');
  readonly padding = signal<CardPadding>('md');
  readonly accent = signal(false);
}

describe('Card', () => {
  let fixture: ComponentFixture<CardHost>;
  let host: CardHost;

  const card = () => fixture.nativeElement.querySelector('cb-card') as HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CardHost] }).compileComponents();
    fixture = TestBed.createComponent(CardHost);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('projects its content', () => {
    expect(card().textContent).toContain('Card content');
  });

  it('defaults to the raised, medium-padded surface with no accent', () => {
    expect(card().classList).not.toContain('cb-card--surface');
    expect(card().classList).not.toContain('cb-card--pad-none');
    expect(card().classList).not.toContain('cb-card--accent');
  });

  it('switches to the flatter surface tone', () => {
    host.tone.set('surface');
    fixture.detectChanges();

    expect(card().classList).toContain('cb-card--surface');
  });

  it('lets a caller with its own internal padding (a stack of sections) turn the card padding off', () => {
    host.padding.set('none');
    fixture.detectChanges();

    expect(card().classList).toContain('cb-card--pad-none');
  });

  it('marks the current item in a list', () => {
    host.accent.set(true);
    fixture.detectChanges();

    expect(card().classList).toContain('cb-card--accent');
  });
});
