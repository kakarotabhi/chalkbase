import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
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
 *
 * ## Nothing here caps how long the wait can be
 *
 * There is no timeout on this call, deliberately. A timeout would resolve the guard as "not signed
 * in" and drop the user on the login screen, where signing in goes to the same server over the
 * same connection and is just as slow — so a slow morning would become a loop between two screens
 * instead of one honest wait. `bootstrapping` below is the answer instead: the wait stays, and the
 * app says what it is waiting for.
 */
@Injectable({ providedIn: 'root' })
export class SessionBootstrap {
  private readonly meApi = inject(MeApi);
  private readonly session = inject(SessionStore);
  private readonly navigation = inject(NavigationStore);

  private inFlight: Observable<boolean> | null = null;

  /** See `refreshAfterForbidden` — the in-flight cache for a `403`-triggered refetch. */
  private forbiddenRefetch: Observable<void> | null = null;

  /** Whether the first `/api/me` has been answered — one way, and only ever set once. */
  private firstSettled = false;

  private readonly firstInFlight = signal(false);

  /**
   * True while the app is still waiting for its first answer about who is signed in.
   *
   * The root component renders a boot state from this. It has to be *this* signal and not
   * `inFlight`: a later refetch — after a sign-out, or after a failure emptied the stores — happens
   * with the shell already on screen and its own state showing, and covering that with a full-page
   * "Loading Chalkbase" would be a step backwards. Only the first call has nothing behind it.
   *
   * It settles on every arm: a session, a 401, or a network failure. That is what `finalize` in
   * `ensure()` is for — putting it in the success path only is how a boot state ends up permanent
   * for the users whose connection failed, which is the population it exists for.
   */
  readonly bootstrapping = this.firstInFlight.asReadonly();

  /**
   * Resolves true when there is a session, false when there is demonstrably none.
   *
   * Never throws: the guard needs a decision, not an exception.
   */
  ensure(): Observable<boolean> {
    if (this.isFresh()) {
      this.settleFirst();
      return of(true);
    }
    if (this.inFlight) {
      return this.inFlight;
    }

    if (!this.firstSettled) {
      this.firstInFlight.set(true);
    }

    this.inFlight = this.meApi.get().pipe(
      tap((me) => {
        this.session.bootstrapped(me);
        this.navigation.load(me.navigation);
      }),
      map(() => true),
      catchError((error: unknown) => of(this.afterFailure(error))),
      finalize(() => {
        this.inFlight = null;
        // Both arms of `afterFailure` end here, and so does the success path: `catchError` returns
        // a value rather than rethrowing, so this stream always completes.
        this.settleFirst();
      }),
      // The guard subscribes, and so does the app initializer that started this call early. One
      // request, both answers.
      shareReplay({ bufferSize: 1, refCount: false }),
    );

    return this.inFlight;
  }

  /**
   * The refetch ADR-0008's staleness rule asks for: some other call came back `403`, so the menu
   * and permissions this tab is holding might be built from a role that no longer applies.
   * `apiErrorInterceptor` calls this before it lets that `403` reach the screen.
   *
   * ## Sharing one refetch
   *
   * A screen that fires several requests on load can have all of them come back `403` at once —
   * the permission they all needed was just revoked. That must not mean several refetches racing
   * each other, so this caches the observable exactly the way `ensure()` caches `inFlight`: the
   * first caller creates it, everyone else gets the same one back, and it clears itself once the
   * call settles so the *next* `403`, later, asks again.
   *
   * It is a separate field from `inFlight` on purpose. `ensure()` calls `isFresh()` first and skips
   * the network whenever the stores already hold a session — which is exactly the situation this
   * method is called in. The whole point here is to ask anyway, because that session is precisely
   * what might be wrong.
   *
   * ## Never throws
   *
   * The caller is already holding the `403` the user needs to see; this must never replace it with
   * a different failure. So a failure here is handled exactly like a failure of `ensure()`'s own
   * call — `afterFailure` clears the session on a genuine 401 and otherwise just logs and keeps
   * whatever this tab already had — and either way this observable completes normally.
   *
   * `/api/me` needs only a valid session (see `MeApi`), so it should not itself answer `403`; if it
   * ever did, `apiErrorInterceptor` does not route back through this method for a `403` on `/api/me`
   * itself, which is what keeps this from ever calling itself.
   */
  refreshAfterForbidden(): Observable<void> {
    if (this.forbiddenRefetch) {
      return this.forbiddenRefetch;
    }

    this.forbiddenRefetch = this.meApi.get().pipe(
      tap((me) => {
        this.session.bootstrapped(me);
        this.navigation.load(me.navigation);
      }),
      map(() => undefined),
      catchError((error: unknown) => {
        this.afterFailure(error);
        return of(undefined);
      }),
      finalize(() => {
        this.forbiddenRefetch = null;
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

    return this.forbiddenRefetch;
  }

  /**
   * Starts the bootstrap without waiting for it.
   *
   * Called at application start so the request is already on the wire by the time the router asks.
   * The router still cannot activate the shell until the answer lands — `authGuard` blocks, and
   * showing an authenticated shell to someone who may not be signed in would be worse than
   * waiting — so what fills the wait is `App`, which renders a boot state from `bootstrapping`
   * outside the router outlet rather than leaving a blank page open for the length of one request
   * on a school's broadband.
   */
  start(): void {
    this.ensure().subscribe();
  }

  /** True when the stores already hold a bootstrapped session, so there is nothing to ask. */
  private isFresh(): boolean {
    return this.session.isSignedIn() && this.navigation.loaded();
  }

  private settleFirst(): void {
    this.firstSettled = true;
    this.firstInFlight.set(false);
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
