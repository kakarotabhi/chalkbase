import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { SessionBootstrap } from './core/auth/session-bootstrap';
import { apiErrorInterceptor } from './core/interceptors/api-error-interceptor';
import { timeoutInterceptor } from './core/interceptors/timeout-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // Order matters: interceptors run outer-to-inner in the order listed, so `apiErrorInterceptor`
    // wraps `timeoutInterceptor` rather than the other way round. That puts the timeout closer to
    // the backend call, so a request that times out flows up *into* `apiErrorInterceptor`'s own
    // `catchError` — logged with a trace id like any other failure, and safe to do so because it
    // is not an `HttpErrorResponse` and so cannot match that interceptor's 401/403 branches. See
    // `timeoutInterceptor`'s doc comment for the number chosen and the one endpoint it leaves
    // alone.
    provideHttpClient(withFetch(), withInterceptors([apiErrorInterceptor, timeoutInterceptor])),
    // Asks the server who is signed in as early as the app can ask, and deliberately returns
    // nothing so Angular does not wait for it: by the time the router's guard needs an answer the
    // request is usually already on the wire. Starting early shortens the wait; it does not remove
    // it, and it never made the app paint sooner — `authGuard` blocks the shell route, so the
    // router has nothing to activate until the answer lands. What fills the wait is `App`, which
    // renders a boot state from `SessionBootstrap.bootstrapping` outside the router outlet
    // (ADR-0010: a layout that reflows correctly but takes eight seconds is not responsive, and a
    // blank one held open for the length of one request is worse).
    provideAppInitializer(() => inject(SessionBootstrap).start()),
  ],
};
