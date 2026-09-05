import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';
import { MeApi } from '../api/me-api';
import { NavigationStore } from '../navigation/navigation-store';
import { SessionStore } from './session-store';

/**
 * Asks the server who is signed in, once, and fills the stores from the answer.
 *
 * ## Why this exists
 *
 * The session is an HttpOnly cookie. Nothing in this app can read it, so "am I signed in?" has
 * exactly one honest answer and it comes from `GET /api/me`. Before this, `SessionStore` was
 * memory the login response filled in, which answered "did *this tab* sign in?" — so a reload
 * signed a perfectly valid session out and bounced it to the login screen.
 *
 * ## Once, not per navigation
 *
 * `ensure()` is called by the route guard, which runs on every navigation. It refetches only when
 * there is nothing to trust: an in-flight call is shared, and a completed one is reused for as
 * long as the stores still hold a session. Signing out empties them, which is what makes the next
 * call ask again — no reset plumbing, and no way for the two to disagree.
 *
 * ## Failure is not sign-out
 *
 * Only the server saying 401 clears the session. A timeout, a 502 or a dead Wi-Fi connection is
 * not evidence that the session ended, and throwing a working user back to a login screen they
 * cannot reach either would turn a blip into a dead end. In that case the answer falls back to
 * what this tab already knows, the shell stays on screen, and the menu is simply empty until a
 * later navigation succeeds.
 */
@Injectable({ providedIn: 'root' })
export class SessionBootstrap {
  private readonly meApi = inject(MeApi);
  private readonly session = inject(SessionStore);
  private readonly navigation = inject(NavigationStore);

  private inFlight: Observable<boolean> | null = null;

  /**
   * Resolves true when there is a session, false when there is demonstrably none.
   *
   * Never throws: the guard needs a decision, not an exception.
   */
  ensure(): Observable<boolean> {
    if (this.isFresh()) {
      return of(true);
    }
    if (this.inFlight) {
      return this.inFlight;
    }

    this.inFlight = this.meApi.get().pipe(
      tap((me) => {
        this.session.bootstrapped(me);
        this.navigation.load(me.navigation);
      }),
      map(() => true),
      catchError((error: unknown) => of(this.afterFailure(error))),
      finalize(() => (this.inFlight = null)),
      // The guard subscribes, and so does the app initializer that started this call early. One
      // request, both answers.
      shareReplay({ bufferSize: 1, refCount: false }),
    );

    return this.inFlight;
  }

  /**
   * Starts the bootstrap without waiting for it.
   *
   * Called at application start so the request is already on the wire by the time the router asks.
   * The shell's chrome — header, account menu, the nav element itself — renders from the first
   * frame and the menu fills in when the answer lands, rather than the whole app holding a blank
   * page open for the length of one request on a school's broadband.
   */
  start(): void {
    this.ensure().subscribe();
  }

  /** True when the stores already hold a bootstrapped session, so there is nothing to ask. */
  private isFresh(): boolean {
    return this.session.isSignedIn() && this.navigation.loaded();
  }

  private afterFailure(error: unknown): boolean {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      // The definitive answer: there is no session. Anything this tab still held is wrong.
      this.session.signedOut();
      this.navigation.clear();
      return false;
    }

    console.error('[bootstrap] /api/me failed; keeping whatever this tab already knows', error);
    return this.session.isSignedIn();
  }
}
