# ADR-0007: One response envelope for every endpoint

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Supersedes: the RFC 9457 problem-detail convention used by the scaffold

## Context

Every `/api/v1` response needs one predictable shape, so the frontend has one place that knows how
to read a success and one that knows how to read a failure.

The scaffold used **RFC 9457 problem details**, which Spring produces natively. The team already
runs an **envelope** (`{success, data, error}`) in MicroMunshi, and its frontend, its support
process and its habits are built around that shape.

## Options considered

1. **RFC 9457 problem details.** Standard, natively supported, and machine-readable by tools that
   already understand it. Covers errors only — successes are bare payloads, so the client reads two
   unrelated shapes.
2. **Envelope on every response.** One shape always. Costs a level of nesting on success, and
   duplicates information that is already in the HTTP status line.
3. **Bare payloads with ad-hoc error bodies.** Rejected — this is the thing both of the above exist
   to prevent.

## Decision

**Option 2, the envelope**, matching the shape already in use in MicroMunshi.

```jsonc
// success
{ "success": true, "data": { … }, "timestamp": "…", "traceId": "…" }

// failure
{ "success": false,
  "error": { "code": "VAL_001", "message": "…", "details": { "code": "must not be blank" } },
  "timestamp": "…", "traceId": "…" }
```

The honest trade-off: RFC 9457 is the better *standard*, and on a greenfield project with no other
codebase to match it would win. It does not win here. One team maintains both products, the same
people read both sets of logs and answer support calls about both, and a second response convention
is a permanent tax on that. Consistency across the two beats standards compliance for a product
whose API has exactly one consumer that we also write.

### Constraints that come with it

These are what make the envelope safe rather than merely familiar.

- **The HTTP status line stays truthful.** A failure is never `200 OK` with `success: false`. That
  habit is the usual reason envelopes get a bad name: it breaks caches, proxies, retry logic,
  monitoring and every HTTP client's error handling. The envelope adds a code and a trace id *on
  top of* the status; it does not replace it.
- **Exactly one of `data` and `error` is present.** The other is omitted, not null.
- **Error codes are API.** Clients branch on `error.code`, never on `error.message`. Renaming a code
  is a breaking change; rewording a message is not.
- **Every response carries a `traceId`**, also returned as `X-Request-Id` and present on every log
  line for that request. When a clerk phones to say "it failed", this is the difference between
  finding the request and guessing.
- **No exception message from a parser or the database is ever echoed.** Jackson quotes the input it
  choked on — echoing that puts a password from a login body into the response and the logs.
  Database messages leak table and column names.

### Error codes are per module, not one global enum

`ErrorCode` is an **interface**. `PlatformErrorCode` holds cross-cutting codes (`VAL_001`,
`AUTH_001`, `PERM_001`, `GEN_001`); each module declares its own (`SCHOOL_001`, `FEE_003`).

One project-wide enum forces sixteen unrelated modules to share a file and, in practice, produces
the specific smell that prompted this: a loan-domain code being returned for a generic optimistic
locking failure, because that was the nearest constant to hand.

### Database constraints are explained by the module that owns them

The global handler contains **no** domain knowledge. It does not know what `uq_school_code` means.
Modules register `ConstraintMappingProvider` beans; the platform builds a lookup at startup and
resolves the constraint name from Hibernate's `ConstraintViolationException.getConstraintName()`.

Two reasons this matters more than it looks:

- A chain of `if (message.contains("uq_…"))` in the platform layer is a file every module has to
  edit — a permanent merge conflict, and a module-boundary violation that `ModularityTests` cannot
  see because it is all one package.
- Matching on driver message *text* is a test that passes today and breaks on a driver upgrade or
  under a different server locale. The constraint name is structured data; use it.

### `IllegalArgumentException` is not mapped to 400

Tempting, and wrong. The JDK and its libraries throw it for genuine programming mistakes —
`Objects.requireNonNull`, `Enum.valueOf` on an unknown constant, `List.of(null)`. Mapping it to 400
reports our own bugs to the client as their mistake, and removes them from the 500 rate that is
supposed to page someone. Business failures throw `ChalkbaseException` with an explicit code.

### 401 and 403 use the envelope too

`@RestControllerAdvice` only sees exceptions from requests that reached a controller. An
unauthenticated request is rejected earlier, inside the security filter chain, so without an
`AuthenticationEntryPoint` and `AccessDeniedHandler` the API returns Spring's default body for
exactly the two failures a client most needs to parse. Both are wired to the same envelope now,
before there is anything to authenticate, so they are correct the moment they start firing.

## Consequences

- Controllers return `ApiResponse<T>` explicitly rather than through a `ResponseBodyAdvice`. Wrapping
  automatically would hide the contract and confuse the generated OpenAPI schema.
- The frontend unwraps in one place (`core/api`), so components still work with plain payloads.
- Paged endpoints will need a `PageResponse<T>` inside `data`; that shape is not designed yet and
  should be settled before the first list endpoint that needs paging.
- Adding a unique index without a matching `ConstraintMapping` degrades to a generic conflict
  message rather than a helpful one. The mapping belongs in the same change as the migration.
