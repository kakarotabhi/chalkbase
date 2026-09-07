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

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch(), withInterceptors([apiErrorInterceptor])),
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
