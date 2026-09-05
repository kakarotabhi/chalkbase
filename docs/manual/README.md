# Chalkbase user manual

Help for the people who use Chalkbase every day. One folder per role, because a class teacher and an
accountant never need the same page.

| Role | Pages |
|---|---|
| [Admin / principal](admin/) | school setup, sessions, classes, staff, reports |
| [Teacher](teacher/) | attendance, marks, timetable, homework, parent messages |
| [Parent](parent/) | fees, attendance, report cards, notices |
| [Student](student/) | timetable, assignments, results |
| [Accountant](accountant/) | fee collection, receipts, concessions, day book |
| [Transport](transport/) | routes, stops, vehicle and driver records |
| [Librarian](librarian/) | catalogue, issue and return, fines |

## Writing a page

- One task per page, named after the task: `collect-fee.md`, not `fees.md`.
- Write for the person doing the job, not the developer who built it. No module names, no API paths.
- Start with the outcome, then the steps, then what to do when it goes wrong.
- Screenshots go in `_assets/screenshots/` and are named `<role>-<task>-<n>.png`.
- **The file path is a contract.** In-app help links to `/help/<role>/<task>`, so renaming a file
  breaks a link — leave a stub that points to the new page.

Pages stay plain Markdown so this folder can be published as a docs site later without moving
anything.
