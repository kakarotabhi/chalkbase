import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SessionStore } from '../../core/auth/session-store';
import { navLabel } from '../../core/navigation/nav-labels';
import { NavLink, NavigationStore } from '../../core/navigation/navigation-store';
import { BottomSheet } from '../../shared/components/bottom-sheet/bottom-sheet';
import { Icon } from '../../shared/components/icon/icon';
import { UserMenu } from '../user-menu/user-menu';

/**
 * How many destinations a bottom bar can hold before it stops being usable.
 *
 * Material's number, and it is about thumbs rather than taste: a sixth item on a 360px screen
 * makes every target too narrow to hit. Above this, the first four by order go in the bar and the
 * rest move into the More sheet (ADR-0010).
 */
const COMPACT_BAR_LIMIT = 5;
const COMPACT_BAR_WITH_MORE = 4;

/** One top-level item, plus whether the compact bar has room for it. */
interface NavEntry {
  readonly link: NavLink;
  readonly inCompactBar: boolean;
}

/**
 * Application shell: header, primary navigation and the routed content area.
 *
 * The menu is whatever `GET /api/me` returned, resolved through the frontend's own route registry
 * (ADR-0008) — there is no hardcoded list here, and there is no permission check here either.
 * **A menu is not a security boundary**: the server enforces every permission independently
 * (ADR-0005), so this component's job is only to draw what it was handed.
 *
 * It draws it once. The same `<nav>` element is the bottom bar under 600px, the icon rail from
 * 600px and the labelled sidebar from 840px; CSS moves it. Rendering it three times would give a
 * screen reader three navigation landmarks and triple the DOM on the cheapest devices, which are
 * exactly the devices that cannot afford it (ADR-0010).
 */
@Component({
  selector: 'cb-main-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, UserMenu, Icon, BottomSheet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.scss',
})
export class MainLayout {
  private readonly session = inject(SessionStore);
  private readonly navigation = inject(NavigationStore);
  private readonly changeDetector = inject(ChangeDetectorRef);

  private readonly moreTrigger = viewChild<ElementRef<HTMLButtonElement>>('moreTrigger');

  /**
   * The school whose data is on screen. Staff at a group of schools sign in one school at a time,
   * so this label is what says which set of children the numbers below belong to.
   *
   * The fallback is the product name, not a blank or a skeleton: the store is empty only in the
   * moment before the bootstrap call lands, and an empty header reads as breakage.
   */
  protected readonly schoolName = computed(() => this.session.schoolName() ?? 'Chalkbase');

  /** Top-level items, in the server's order. Empty until `/api/me` answers, and if it fails. */
  protected readonly navItems = this.navigation.items;

  protected readonly needsMore = computed(() => this.navItems().length > COMPACT_BAR_LIMIT);

  /**
   * Every item, tagged with whether the bottom bar has room for it.
   *
   * The tag is a class rather than a second list: the rail and the sidebar show everything, and
   * the bar hides the overflow in CSS. One list in the DOM, three arrangements.
   */
  protected readonly navEntries = computed<readonly NavEntry[]>(() => {
    const items = this.navItems();
    const overflowing = items.length > COMPACT_BAR_LIMIT;
    return items.map((link, index) => ({
      link,
      inCompactBar: !overflowing || index < COMPACT_BAR_WITH_MORE,
    }));
  });

  protected readonly moreOpen = signal(false);

  protected readonly moreLabel = navLabel('nav.more');
  protected readonly sheetHeading = navLabel('nav.more.heading');
  protected readonly sheetCloseLabel = navLabel('nav.more.close');

  protected openMore(): void {
    this.moreOpen.set(true);
  }

  /**
   * Closes the sheet and puts focus back where it came from.
   *
   * Without this, dismissing the sheet drops focus on `<body>` and a keyboard user starts again at
   * the top of the page. The sheet itself deliberately does not do this — only whatever opened it
   * knows what to go back to.
   */
  protected closeMore(): void {
    if (!this.moreOpen()) {
      return;
    }
    this.moreOpen.set(false);
    // The sheet is behind an `@if`, so it is in the DOM until this view is flushed. Focusing before
    // that would put focus on an element that is about to be removed.
    this.changeDetector.detectChanges();
    this.moreTrigger()?.nativeElement.focus();
  }
}
