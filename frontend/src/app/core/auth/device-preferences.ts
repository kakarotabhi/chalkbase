import { Injectable } from '@angular/core';

const SCHOOL_CODE_KEY = 'cb.auth.schoolCode';
const KEEP_SIGNED_IN_KEY = 'cb.auth.keepSignedIn';

/**
 * The handful of things Chalkbase remembers about *this device* — not about the user, and never
 * anything secret.
 *
 * A parent types their school code once and never again; a teacher on the office desktop gets it
 * pre-filled too. Nothing here is authentication: the session lives in an HttpOnly cookie the
 * server sets, which this class cannot see and must not try to duplicate.
 *
 * Every access is wrapped, because `localStorage` is not a safe call. Safari in private browsing
 * and Chrome with third-party storage blocked throw on *read* as well as write, and an uncaught
 * throw here would take the sign-in screen down for exactly the users least able to recover from
 * a blank page.
 */
@Injectable({ providedIn: 'root' })
export class DevicePreferences {
  /** The school code last signed in with on this device, or null if there is none to offer. */
  schoolCode(): string | null {
    return this.read(SCHOOL_CODE_KEY);
  }

  rememberSchoolCode(code: string): void {
    this.write(SCHOOL_CODE_KEY, code.trim().toUpperCase());
  }

  keepSignedIn(): boolean {
    return this.read(KEEP_SIGNED_IN_KEY) === 'true';
  }

  setKeepSignedIn(keep: boolean): void {
    this.write(KEEP_SIGNED_IN_KEY, String(keep));
  }

  private read(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private write(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      // Storage is unavailable or full. Remembering is a convenience — losing it must never stop
      // someone signing in, so this is swallowed deliberately rather than surfaced.
    }
  }
}
