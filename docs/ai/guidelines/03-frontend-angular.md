# Frontend Guidelines: Angular

## Angular Version Rule

Before giving Angular-specific implementation guidance or writing Angular code, inspect the project version from `package.json` and Angular CLI output if available.

For a new project:

- Use Angular CLI.
- Do not pin a version unless the user requests it.
- Use the latest stable Angular available to the environment.

For an existing project:

- Follow the installed Angular version.
- Do not introduce APIs unsupported by that version.
- Match the local architecture and component library.

## Project Organization

Prefer feature-based organization.

Example:

```text
src/app
  core
  shared
  features
    admissions
    students
    attendance
    fees
    exams
    communication
    compliance
    transport
    hostel
    library
    inventory
  layout
```

Use `core` for application-wide providers and infrastructure. Use `shared` for reusable UI utilities that are not domain-specific. Keep domain screens inside feature folders.

## Angular Style

Follow the official Angular style guide:

- Use hyphenated file names.
- Keep one concept per file.
- Group related component files together.
- Organize by feature area.
- Keep components focused on presentation and interaction.
- Move reusable business logic to services or pure functions.
- Prefer `inject()` where the project style and Angular version support it.
- Keep template logic simple.

Official reference: <https://angular.dev/style-guide>

## Component Rules

Components should:

- Render UI.
- Manage local UI state.
- Call feature facades/services.
- Expose typed inputs and outputs.
- Avoid direct knowledge of API transport details.
- Avoid large templates with embedded business logic.

Do not put fee calculations, exam result formulas, permission logic, or compliance rules inside Angular components. These rules belong on the backend. Frontend may preview calculations only when clearly marked and validated again by the server.

## State Management

Default approach:

- Use signals for local component state when supported.
- Use computed values for derived UI state.
- Use services or feature facades for shared state.
- Add a larger state library only when real cross-screen complexity exists.

Rules:

- Do not duplicate server truth in long-lived client state.
- Clear sensitive state on logout and role switch.
- Do not cache sensitive student/finance/health data in local storage.
- Use local storage only for harmless preferences.

## Forms

School ERP forms are often long and sensitive. Forms must be deliberate.

Use:

- Typed reactive forms or the project-standard form strategy.
- Signal forms only if the installed Angular version supports them and the project has adopted them.
- Server-side validation as the source of truth.
- Autosave drafts only for long non-financial forms, and only when privacy expectations are satisfied.

Form requirements:

- Show field-level validation.
- Preserve entered data after recoverable errors.
- Use clear labels.
- Group long forms into sections.
- Avoid hidden required fields.
- Support file upload progress and retry.
- Confirm before discarding dirty changes.
- Mask sensitive identity, health, category, and finance fields unless the user has permission.

## Routing

Rules:

- Use lazy-loaded feature routes.
- Use route guards for UX and first-line restriction.
- Never rely on route guards as the only authorization layer.
- Use resolvers only when they improve UX and error handling.
- Keep route data typed where practical.
- Use clear route names based on domain terms.

Example route areas:

- `/students`
- `/admissions`
- `/attendance`
- `/fees`
- `/exams`
- `/communication`
- `/compliance`
- `/settings`

## API Client Rules

- Prefer generated API types from OpenAPI or a strongly typed client.
- Centralize HTTP interceptors.
- Attach auth/session context consistently.
- Handle 401, 403, 404, 409, 422, and 500 responses predictably.
- Display validation errors from backend field paths.
- Do not construct URLs ad hoc across components.
- Do not trust frontend-calculated totals for final submissions.

## UI and UX Rules

Design for daily school operations:

- Dense but readable admin tables.
- Fast student search.
- Mobile-friendly teacher attendance.
- Simple parent portal.
- Clear fee due and receipt status.
- Clear lock/publish statuses for attendance and exams.
- Obvious distinction between draft, submitted, approved, locked, and published.

Avoid:

- Marketing-page layouts inside the ERP.
- Decorative UI that reduces scanning speed.
- Hidden actions for high-frequency workflows.
- Destructive actions without confirmation and audit reason.

## Accessibility

Requirements:

- Use semantic HTML through Angular components.
- Provide labels for inputs.
- Ensure keyboard navigation.
- Keep color contrast sufficient.
- Do not rely only on color for status.
- Use ARIA attributes for custom widgets.
- Manage focus in dialogs and drawers.
- Test important forms at mobile widths.

## Tables and Lists

ERP lists must support:

- Pagination.
- Search.
- Filters.
- Sort.
- Column visibility where useful.
- Bulk actions with permission checks.
- Export only for permitted users.
- Empty states.
- Loading states.
- Error states.

Do not load unbounded lists.

## Sensitive Frontend Handling

- Do not place tokens in unsafe storage.
- Do not log API responses containing sensitive data.
- Do not expose hidden form fields with values the user cannot see.
- Do not include sensitive values in URLs.
- Do not persist payment or identity data in browser storage.
- Clear sensitive views on logout.

## Build and Verification

After Angular changes:

- Run the project build command.
- Run relevant tests if present.
- Check lint/format command if configured.
- Verify mobile layout for parent and teacher workflows when affected.

## Official References

- Angular documentation: <https://angular.dev/>
- Angular style guide: <https://angular.dev/style-guide>
- Angular routing guide: <https://angular.dev/guide/routing>

