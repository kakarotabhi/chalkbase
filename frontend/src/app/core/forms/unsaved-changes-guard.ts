import { CanDeactivateFn } from '@angular/router';

/** A screen that can be in the middle of something the user would not want to lose. */
export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

/**
 * What the user is asked. Plain, and it says what happens rather than what we are about to do:
 * "discard" is the word people scan for when they are deciding whether to click.
 */
export const LEAVE_CONFIRMATION = 'You have unsaved changes. Leave this page and discard them?';

/**
 * Stops a half-finished form from disappearing when someone taps a menu item.
 *
 * On the router only, deliberately. A browser reload or a closed tab is the component's own
 * `beforeunload` — the router cannot see those, and the browser will not let us word the message
 * anyway.
 *
 * `window.confirm` rather than a designed dialog: this has to be able to block the navigation
 * synchronously, and until there is a modal in `shared/components` that can, the native prompt is
 * the honest option. Replacing it later changes this file and nothing else.
 */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) =>
  !component.hasUnsavedChanges() || window.confirm(LEAVE_CONFIRMATION);
