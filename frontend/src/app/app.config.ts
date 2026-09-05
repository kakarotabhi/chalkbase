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
    // nothing so Angular does not wait for it. By the time the router's guard needs an answer the
    // request is usually already on the wire, and the shell paints its header and empty navigation
    // meanwhile rather than holding a blank page open for the length of one request (ADR-0010:
    // a layout that reflows correctly but takes eight seconds is not responsive).
    provideAppInitializer(() => inject(SessionBootstrap).start()),
  ],
};
