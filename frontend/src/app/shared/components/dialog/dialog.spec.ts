import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Dialog } from './dialog';

@Component({
  imports: [Dialog],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" id="trigger" (click)="open.set(true)">Make current</button>
    @if (open()) {
      <cb-dialog
        heading="Make 2026–27 the current session?"
        confirmLabel="Make it current"
        cancelLabel="Keep 2025–26"
        [busy]="busy()"
        (confirmed)="onConfirmed()"
        (cancelled)="onCancelled()"
      >
        <p id="consequence">Everyone at this school will see 2026–27 from now on.</p>
      </cb-dialog>
    }
  `,
})
class Host {
  readonly open = signal(true);
  readonly busy = signal(false);
  confirms = 0;
  cancels = 0;

  onConfirmed(): void {
    this.confirms++;
  }

  onCancelled(): void {
    this.cancels++;
    this.open.set(false);
  }
}

describe('Dialog', () => {
  let fixture: ComponentFixture<Host>;

  const element = () => fixture.nativeElement as HTMLElement;
  const host = () => fixture.componentInstance;
  const panel = () => element().querySelector('.dialog__panel');
  const dialog = () => element().querySelector('cb-dialog');
  const button = (label: string) =>
    Array.from(element().querySelectorAll('cb-dialog button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement;

  const press = (key: string) =>
    panel()?.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    // Attached to the document body, because focus is only real in a document that has focus.
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.nativeElement.remove();
  });

  it('is a modal dialog named and described by what it says', () => {
    expect(panel()?.getAttribute('role')).toBe('dialog');
    expect(panel()?.getAttribute('aria-modal')).toBe('true');

    const labelledBy = panel()?.getAttribute('aria-labelledby');
    expect(element().querySelector(`#${labelledBy}`)?.textContent?.trim()).toBe(
      'Make 2026–27 the current session?',
    );

    // The consequence is part of the accessible description, not something a screen-reader user
    // has to go looking for after the heading has been read out.
    const describedBy = panel()?.getAttribute('aria-describedby');
    expect(element().querySelector(`#${describedBy}`)?.textContent).toContain(
      'will see 2026–27 from now on',
    );
  });

  /**
   * The whole reason this exists rather than `window.confirm`, whose button says "OK". The verb
   * is what makes the consequence legible at the moment of clicking.
   */
  it('lets the caller name both answers', () => {
    expect(button('Make it current')).toBeTruthy();
    expect(button('Keep 2025–26')).toBeTruthy();
    expect(element().textContent).not.toContain('OK');
  });

  it('confirms when the confirm button is pressed', () => {
    button('Make it current').click();
    fixture.detectChanges();

    expect(host().confirms).toBe(1);
    expect(host().cancels).toBe(0);
  });

  it('cancels from the cancel button, from Escape and from the page behind it', () => {
    button('Keep 2025–26').click();
    fixture.detectChanges();
    expect(host().cancels).toBe(1);

    host().open.set(true);
    fixture.detectChanges();
    press('Escape');
    fixture.detectChanges();
    expect(host().cancels).toBe(2);

    host().open.set(true);
    fixture.detectChanges();
    element().querySelector<HTMLElement>('.dialog__scrim')?.click();
    fixture.detectChanges();
    expect(host().cancels).toBe(3);

    expect(host().confirms).toBe(0);
  });

  /**
   * Focus is trapped by the CDK rather than by hand (ADR-0009), and it captures the first tabbable
   * element in the panel — which is Cancel, by DOM order. So an Enter still travelling from the
   * button that opened the dialog cannot go ahead with anything, which is the point of asking.
   *
   * What is asserted is the arrangement that makes that true: the trap is installed, and Cancel is
   * the first control inside it. Where focus physically lands is not assertable here — the CDK
   * decides what is tabbable by measuring whether an element is visible, and jsdom gives every
   * element a zero-sized box, so nothing in it is ever tabbable. That part is verified in a real
   * browser.
   */
  it('is trapped, with the safe answer first inside the trap', () => {
    expect(element().querySelectorAll('.cdk-focus-trap-anchor').length).toBe(2);

    const first = panel()?.querySelector('button');
    expect(first?.textContent).toContain('Keep 2025–26');
  });

  it('asks to be closed rather than closing itself', () => {
    // The caller owns whether it is open, so it can refuse — and so returning focus to whatever
    // opened it stays with the only code that knows what that was.
    host().onCancelled = () => {
      host().cancels++;
    };

    button('Keep 2025–26').click();
    fixture.detectChanges();

    expect(dialog()).not.toBeNull();
  });

  /**
   * While the request it guards is in flight the dialog stays open and stays readable. Closing on
   * click would leave the user with nothing on screen to explain the wait, and a second click must
   * not send the request twice.
   */
  it('stops answering while the request it guards is in flight', () => {
    host().busy.set(true);
    fixture.detectChanges();

    expect(button('Make it current').disabled).toBe(true);
    expect(button('Keep 2025–26').disabled).toBe(true);

    press('Escape');
    element().querySelector<HTMLElement>('.dialog__scrim')?.click();
    fixture.detectChanges();

    expect(host().cancels).toBe(0);
    expect(host().confirms).toBe(0);
    expect(dialog()).not.toBeNull();
  });
});
