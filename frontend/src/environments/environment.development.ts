/** Dev environment. `/api` is proxied to the Spring Boot app on :8080 by proxy.conf.json. */
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
} as const;
