import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { AuthApi } from '../../core/api/auth-api';
import { SessionStore } from '../../core/auth/session-store';

/** Shown in place of a name when the store is empty — a reload, or a shell rendered in a test. */
const UNKNOWN_USER = 'Account';

let nextMenuId = 0;

/**
 * Initials for the avatar: first letter of the first name, first letter of the last.
 *
 * Indian names run to three and four parts ("Raja Kumar Mishra"), and the middle ones are the
 * least identifying, so first-and-last beats first-two. A single-word name gets one letter rather
 * than a doubled one.
 */
function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return '';
  }
  const first = parts[0].charAt(0);
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : '';
  return (first + last).toUpperCase();
}

/**
 * The account menu in the shell header: who is signed in, and the way out.
 *
 * Built by hand rather than on `@angular/cdk/menu`, deliberately. The approved design anchors the
 * panel to the trigger with a fixed offset inside the header, so there is no overlay to position,
 * and a menu button must *not* trap focus — the WAI-ARIA pattern moves focus into the menu and
 * hands it back on Escape. What is left is a roving-focus list, which is a keydown switch. The two
 * things `AGENTS.md` forbids hand-rolling, focus traps and overlay positioning, are the two things
 * this does not do; CDK's overlay would also have meant adding `overlay-prebuilt.css`, the one
 * stylesheet the CDK does ship.
 */
@Component({
  selector: 'cb-user-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './user-menu.html',
  styleUrl: './user-menu.scss',
  host: {
    '(document:click)': 'onDocumentClick($event)',
    '(focusout)': 'onFocusOut($event)',
  },
})
export class UserMenu {
  private readonly session = inject(SessionStore);
  private readonly authApi = inject(AuthApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly changeDetector = inject(ChangeDetectorRef);

  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly items = viewChildren<ElementRef<HTMLButtonElement>>('menuItem');

  protected readonly menuId = `cb-user-menu-${++nextMenuId}`;
  protected readonly identityId = `${this.menuId}-identity`;

  protected readonly open = signal(false);
  protected readonly signingOut = signal(false);

  protected readonly displayName = computed(() => this.session.user()?.displayName ?? UNKNOWN_USER);
  protected readonly schoolName = computed(() => this.session.schoolName());
  protected readonly initials = computed(() => initialsOf(this.displayName()));

  protected toggle(): void {
    if (this.open()) {
      this.close({ returnFocus: false });
    } else {
      this.openAt(0);
    }
  }

  protected onTriggerKeydown(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.openAt(0);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.openAt(-1);
        break;
      case 'Enter':
      case ' ':
      case 'Spacebar':
        // Handled here rather than left to the button's own activation so that opening always
        // lands focus on the first item. `preventDefault` stops the browser synthesising the
        // click that would toggle it straight back shut.
        event.preventDefault();
        this.openAt(0);
        break;
      case 'Escape':
        this.close({ returnFocus: false });
        break;
      default:
        break;
    }
  }

  protected onMenuKeydown(event: KeyboardEvent): void {
    const items = this.items();
    const current = items.findIndex((item) => item.nativeElement === document.activeElement);

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.focusItem(current + 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.focusItem(current - 1);
        break;
      case 'Home':
        event.preventDefault();
        this.focusItem(0);
        break;
      case 'End':
        event.preventDefault();
        this.focusItem(-1);
        break;
      case 'Escape':
        event.preventDefault();
        this.close({ returnFocus: true });
        break;
      case 'Tab':
        // Let focus go where the user asked; the menu simply stops being open.
        this.close({ returnFocus: false });
        break;
      default:
        break;
    }
  }

  /**
   * Signs out, and does so even when the network does not cooperate.
   *
   * The server call is what actually ends the session, but if it fails the user is still standing
   * in front of a screen they asked to leave — on a shared computer at the school office that is
   * the whole point of the button. So the local state is cleared and the app navigates whichever
   * way the request goes. A session left alive on the server expires on its own; a signed-in shell
   * left on the screen does not.
   */
  protected signOut(): void {
    if (this.signingOut()) {
      return;
    }
    this.signingOut.set(true);
    this.close({ returnFocus: false });

    this.authApi
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.finishSignOut(),
        error: () => this.finishSignOut(),
      });
  }

  protected onDocumentClick(event: Event): void {
    if (!this.open()) {
      return;
    }
    const target = event.target;
    if (target instanceof Node && this.host.nativeElement.contains(target)) {
      return;
    }
    this.close({ returnFocus: false });
  }

  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget;
    // A null `relatedTarget` is focus going nowhere in particular — including the blur the browser
    // fires as the panel is removed from the DOM. Closing on that would fight our own Escape
    // handling, which puts focus back on the trigger a moment later.
    if (!this.open() || !(next instanceof Node)) {
      return;
    }
    if (!this.host.nativeElement.contains(next)) {
      this.close({ returnFocus: false });
    }
  }

  private finishSignOut(): void {
    this.signingOut.set(false);
    this.session.signedOut();
    void this.router.navigateByUrl('/login');
  }

  /** Opens the panel and puts focus on an item. Negative indexes count back from the end. */
  private openAt(index: number): void {
    this.open.set(true);
    // The panel is behind an `@if`, so it does not exist until the view is rendered. Flushing this
    // component's view here keeps opening synchronous — an item cannot be focused before the
    // browser has been told it exists.
    this.changeDetector.detectChanges();
    this.focusItem(index);
  }

  private close(options: { returnFocus: boolean }): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    if (options.returnFocus) {
      this.changeDetector.detectChanges();
      this.trigger().nativeElement.focus();
    }
  }

  /** Moves focus within the menu, wrapping at both ends the way a menu is expected to. */
  private focusItem(index: number): void {
    const items = this.items();
    if (items.length === 0) {
      return;
    }
    const wrapped = ((index % items.length) + items.length) % items.length;
    items[wrapped].nativeElement.focus();
  }
}
