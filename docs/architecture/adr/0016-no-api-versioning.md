# ADR-0016: No API versioning

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Related: [ADR-0007](0007-api-response-envelope.md), [ADR-0013](0013-external-provider-ports.md)

## Context

The question was how to run `v1`, `v2`, … `vn` side by side: a directory per version, a config flag
to disable one, and a deletion that touches nothing else.

That design works, and it is not free. It costs a duplicated web layer on every version bump (the
duplication is load-bearing — sharing it is what breaks deletability), architecture tests to keep
versions out of the domain, a deprecation lifecycle with `Deprecation`/`Sunset` headers, a support
window measured in academic sessions, and a test matrix multiplied by the number of live versions.

All of that buys exactly one thing: **the ability to break an API for a consumer you cannot make
upgrade.**

Chalkbase has no such consumer. There is one client — the Angular application in this repository —
built, tested and deployed from the same commit as the backend. A breaking change is a single
atomic change to both sides, verified by CI before either ships. Versioning would let us serve an
old contract to a client that cannot exist.

## Decision

**Do not version the API.** No `v2`, no deprecation machinery, no per-version packages, no
enable/disable flags.

### What replaces it: additive evolution

The API changes without breaking anyone, by discipline rather than by mechanism.

- **Add, do not remove or rename.** A new field is safe; removing or renaming one is not.
- **Both sides are tolerant readers.** `spring.jackson.deserialization.fail-on-unknown-properties`
  is already `false`, and the frontend must ignore fields it does not model. An added field is then
  never a breaking change.
- **A genuinely breaking change is made atomically** across backend and frontend in one pull
  request. CI runs both suites; if the pair is inconsistent, it does not merge.
- **The envelope stays stable regardless** ([ADR-0007](0007-api-response-envelope.md)): `success`,
  `error`, `traceId` never change shape, so client error handling is unaffected by any of this.

### The `/api/v1` prefix stays, frozen

Paths keep the literal `/api/v1` prefix they have today, and it never changes.

This is deliberate and is not a hedge. Removing it would be a breaking change to every endpoint
**today**, touching the backend, the frontend base URL, the OpenAPI spec and the design mockups, in
order to avoid a hypothetical breaking change later. Keeping it costs nothing, changes no code, and
leaves the only free hook available should the trigger below ever fire.

Treat `v1` as part of the base path, not as a version number. There will be no `v2` under this ADR.

### Webhooks are the one exception, and they are not a version

[ADR-0013](0013-external-provider-ports.md) introduces webhook endpoints that a payment or messaging
provider calls. Those *are* URLs an outside party depends on — registered in the provider's dashboard,
not something we can change by shipping a frontend.

They are still not a versioning problem:

- The payload shape belongs to the **provider**, not to us, so our contract cannot break it.
- A webhook URL is configuration on their side. Changing it means updating a dashboard, not
  supporting two shapes at once.
- They sit on their own stable paths and are excluded from the additive-evolution rule above,
  because nothing about them is ours to evolve.

Give a webhook endpoint a path that will never need to change, and treat any change to one as an
operational task with a provider, not an API release.

## The trigger that reverses this

Revisit the moment a consumer exists that we cannot force to upgrade:

- **A parent or student mobile app.** This is the likely one. Parents do not update apps; two-year-old
  builds will still be calling the API, and that alone justifies the full design.
- **A board or government integration** — UDISE+, APAAR — moving on its own schedule.
- **A school's IT department scripting against the API**, or any third-party integration we publish.

None exist today, and a PWA served from the same deployment does not count — it updates when the
page reloads.

When one appears, the design brainstormed alongside this ADR is what to build: path versioning, one
package per version inside each module's `api/`, versions kept out of the domain by an architecture
test, a config-driven `active | deprecated | disabled` lifecycle emitting `Deprecation` (RFC 9745)
and `Sunset` (RFC 8594) headers, and sunset dates aligned to the April session boundary so no school
is asked to migrate mid-session.

## Consequences

- Breaking changes are cheap **only while the one client ships with the server**. If the frontend is
  ever deployed independently of the backend, that assumption is gone and this ADR is void even
  without a third-party consumer.
- The discipline is now the safety net, so it needs to be real: adding a field is routine, removing
  one is a deliberate act that changes the frontend in the same pull request.
- There is no mechanism preventing a breaking change from reaching a client that has not shipped.
  The generated OpenAPI client is what makes that visible — a contract change that breaks the
  frontend fails the frontend build.
- Anyone who proposes `v2` should be pointed here first. Re-deciding this every six months is its
  own cost.
