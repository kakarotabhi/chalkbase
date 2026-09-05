# ADR-0001: Modular monolith with Spring Modulith

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja

## Context

Chalkbase covers 16+ domains (admissions, fees, attendance, exams, transport, hostel, library,
payroll, compliance…). That breadth invites a microservice split, but the product is built by a very
small team, deploys to a single VPS via Coolify, and has near-zero independent-scaling needs: a
school's peak load is fee collection week, which hits one module.

The failure mode to avoid is not "monolith" — it is a monolith whose modules quietly grow into each
other until nothing can be changed or extracted.

## Options considered

1. **Layered monolith** (controller/service/repository packages at the top level) — familiar, and
   the fastest way to end up with a service layer where everything calls everything.
2. **Microservices** — real isolation, at the cost of network calls, distributed transactions,
   multiple deploys and per-service infrastructure, all before the first school is onboarded.
3. **Modular monolith with enforced boundaries** — one deployable, but modules that are compile-time
   verified to depend on each other only through declared interfaces.

## Decision

Option 3, using **Spring Modulith**. Each direct sub-package of `in.chalkbase` is an application
module. Cross-module access is allowed only via a module's `api` named interface or a domain event.
`ModularityTests` runs `ApplicationModules.verify()` on every build, so a violation breaks CI rather
than review.

A Maven multi-module build was rejected: it enforces the same boundaries at the cost of ceremony on
all 16 modules, and Modulith's verification gives the same guarantee with one test.

## Consequences

- Extraction stays cheap. A module that ever needs to be a service already has a declared interface
  and no reach-ins to untangle.
- The boundary test will fail during honest refactors. The fix is always the dependency, never the
  test.
- Durable cross-module events (`spring-modulith-starter-jpa`) are not enabled yet — the event
  publication registry needs its own table, and that migration lands with the first published domain
  event. Until then, cross-module calls go through `api` interfaces.
