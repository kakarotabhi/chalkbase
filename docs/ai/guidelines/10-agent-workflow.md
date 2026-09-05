# Agent Workflow

## Working Mode

Agents should act like careful maintainers of a production system. Read before editing, keep changes scoped, verify results, and report clearly.

## Before Editing

Do:

- Read `AGENTS.md`.
- Read the relevant guideline files.
- Read the relevant requirement files.
- Inspect existing code, tests, and configuration.
- Understand current patterns before adding new ones.
- Check for local uncommitted changes if this is a Git repo.

Do not:

- Rewrite unrelated code.
- Move files for style preference only.
- Add dependencies without need.
- Change generated files manually unless that is the project pattern.
- Assume one board/state/school policy is universal.

## Planning

For small changes, proceed directly after inspection.

For larger changes, state a short plan with:

- Files/modules likely affected.
- Data model or API impact.
- Test strategy.
- Risks or assumptions.

Update the plan as work progresses.

## Editing Rules

- Keep patches focused.
- Prefer existing project patterns.
- Use framework conventions.
- Add migrations with schema changes.
- Add or update tests with behavior changes.
- Update docs when behavior/config/deployment changes.
- Preserve unrelated user changes.

## Command Rules

Use fast local tools:

- Use `rg` for search.
- Use targeted file reads.
- Run the smallest relevant test first.
- Run broader build/test commands before finishing when feasible.

Do not leave long-running processes active unless the user requested a dev server and you report the URL.

## Dependency Changes

Before adding a dependency:

1. Check existing dependencies.
2. Check if the framework already solves it.
3. Confirm license and maintenance.
4. Add only what is required.
5. Update lockfiles through the package manager.
6. Verify build/tests.

## Database Change Workflow

For schema changes:

1. Create migration.
2. Add constraints/indexes.
3. Add entity/repository changes.
4. Add service logic.
5. Add tests.
6. Validate migration.

For risky production changes:

- Use expand/migrate/contract.
- Back up before migration.
- Avoid long locks.
- Document rollback limits.

## API Change Workflow

For API changes:

1. Update DTOs.
2. Update controller/service.
3. Update validation.
4. Update OpenAPI.
5. Update frontend client/types.
6. Add tests.
7. Check backward compatibility.

## Frontend Change Workflow

For Angular changes:

1. Inspect Angular version and existing patterns.
2. Use Angular CLI when scaffolding.
3. Keep feature files together.
4. Use typed API models.
5. Handle loading, empty, error, and permission states.
6. Check mobile layout for teacher/parent workflows.
7. Run build/tests/lint as configured.

## Backend Change Workflow

For Spring Boot changes:

1. Add or update request/response DTOs.
2. Validate input.
3. Enforce authorization in the backend.
4. Implement domain logic in services/domain components.
5. Persist through repositories.
6. Emit audit/domain events.
7. Add tests.
8. Update docs/OpenAPI.

## Security-Sensitive Workflow

For auth, permissions, payments, uploads, child data, health data, payroll, or compliance:

- Add negative permission tests.
- Add audit logs.
- Mask sensitive fields.
- Check object-level authorization.
- Check tenant/school/session scoping.
- Confirm no secrets or sensitive values are logged.
- Confirm exports are logged.

## Final Response Checklist

When finishing, report:

- What changed.
- Files created/edited.
- Tests/builds run.
- Anything not run and why.
- Important assumptions.

Keep the final answer concise and actionable.

