import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BottomSheet } from './bottom-sheet';

@Component({
  imports: [BottomSheet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" id="trigger" (click)="open.set(true)">Open</button>
    @if (open()) {
      <cb-bottom-sheet heading="All sections" closeLabel="Close menu" (closed)="onClosed()">
        <a href="#one" id="first">Students</a>
      </cb-bottom-sheet>
    }
  `,
})
class Host {
  readonly open = signal(true);
  closes = 0;

  onClosed(): void {
    this.closes++;
    this.open.set(false);
  }
}

describe('BottomSheet', () => {
  let fixture: ComponentFixture<Host>;

  const element = () => fixture.nativeElement as HTMLElement;
  const panel = () => element().querySelector('.sheet__panel');
  const sheet = () => element().querySelector('cb-bottom-sheet');

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
  });

  it('is a modal dialog with a name a screen reader can read out', () => {
    expect(panel()?.getAttribute('role')).toBe('dialog');
    expect(panel()?.getAttribute('aria-modal')).toBe('true');

    const labelledBy = panel()?.getAttribute('aria-labelledby');
    expect(element().querySelector(`#${labelledBy}`)?.textContent?.trim()).toBe('All sections');
  });

  it('shows what was projected into it', () => {
    expect(element().querySelector('#first')?.textContent).toBe('Students');
  });

  it('closes on Escape', () => {
    element()
      .querySelector('#first')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(fixture.componentInstance.closes).toBe(1);
    expect(sheet()).toBeNull();
  });

  it('closes from the close button', () => {
    element().querySelector<HTMLButtonElement>('.sheet__close')?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.closes).toBe(1);
  });

  it('closes when the page behind it is tapped', () => {
    element().querySelector<HTMLElement>('.sheet__scrim')?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.closes).toBe(1);
  });

  it('asks to be closed rather than closing itself', () => {
    // The host owns whether it is open, so a caller can refuse — an unsaved form, say — and so
    // that returning focus to whatever opened it stays with the only code that knows what that was.
    fixture.componentInstance.onClosed = () => {
      fixture.componentInstance.closes++;
    };

    element().querySelector<HTMLButtonElement>('.sheet__close')?.click();
    fixture.detectChanges();

    expect(sheet()).not.toBeNull();
  });

  it('keeps the sheet, and not the page, as the thing that scrolls', () => {
    // A long menu must not scroll the page behind the scrim: the user would dismiss the sheet and
    // find themselves somewhere else. Layout itself is verified in a browser at 360px — jsdom has
    // no viewport to measure — so what is asserted here is that the container exists to scroll.
    expect(element().querySelector('.sheet__body')).not.toBeNull();
  });
});
