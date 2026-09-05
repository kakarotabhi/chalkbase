# ADR-0003: Authentication and authorization

- Status: Proposed — not yet decided
- Date: 2026-09-05
- Deciders: Raja

## Context

Chalkbase has ten-plus distinct roles (principal, class teacher, accountant, transport manager,
librarian, parent, student, auditor…) whose permissions vary by school, and parents need access from
low-end phones. The current `SecurityConfig` permits everything — a scaffold placeholder that must
not reach a deployment.

## Open questions

- Session cookies or JWT? Parents on flaky mobile networks argue for long-lived refresh; auditors
  argue for revocability.
- Self-hosted identity (Spring Authorization Server, Keycloak) or in-app users? A single-school
  self-hosted deployment should not need a second service.
- Permission model: role-based, or role + fine-grained permission grants per school? The requirement
  pack implies the latter (`docs/requirements/02-functional-requirements.md`).
- Parent/student login by phone number + OTP, which is what Indian schools actually expect.

## Decision

Pending. Blocked until the identity module is scheduled.

Until then, `SecurityConfig` carries a `TODO(identity)` and the app must not be exposed publicly.
