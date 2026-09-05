# AI agent reference

How instructions for AI agents are organised in this repo. Three tiers, by how often they are needed:

| Tier | Where | Contents |
|---|---|---|
| Always loaded | `/AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md` | Short. Commands, non-negotiable rules, pointers. The scoped files load only when working in that folder. |
| On demand | `docs/ai/guidelines/` | Deep reference per topic. Linked from AGENTS.md, read when relevant. |
| Tooling | `.claude/` | Claude Code specifics: subagents, commands, hooks, permissions. Not tool-neutral, so it stays out of AGENTS.md. |

`AGENTS.md` is the source of truth — Claude Code, Codex, Cursor and Copilot all read it. `CLAUDE.md`
is a pointer to it and holds no rules of its own.

## Keeping it useful

- Keep the AGENTS.md files short. Everything that is occasionally relevant belongs in `guidelines/`.
- A rule that a formatter or a test can enforce should be enforced there instead of written down.
- When a module grows its own hard-won rules, give it a nested `AGENTS.md` next to the code rather
  than growing the root file.
- `docs/architecture/module-map.md` is maintained by hand so agents can look up ownership instead of
  scanning the backend.

## Guidelines index

| File | Read it when |
|---|---|
| [00-project-context.md](guidelines/00-project-context.md) | you need the product background |
| [01-engineering-principles.md](guidelines/01-engineering-principles.md) | making a design trade-off |
| [02-backend-spring-boot.md](guidelines/02-backend-spring-boot.md) | writing Java |
| [03-frontend-angular.md](guidelines/03-frontend-angular.md) | writing Angular |
| [04-database-postgres.md](guidelines/04-database-postgres.md) | writing a migration or a query |
| [05-api-contracts.md](guidelines/05-api-contracts.md) | adding or changing an endpoint |
| [06-security-privacy-compliance.md](guidelines/06-security-privacy-compliance.md) | touching personal data, auth or audit |
| [07-testing-quality.md](guidelines/07-testing-quality.md) | writing tests |
| [08-devops-coolify.md](guidelines/08-devops-coolify.md) | changing build or deployment |
| [09-domain-rules-school-india.md](guidelines/09-domain-rules-school-india.md) | modelling anything school-specific |
| [10-agent-workflow.md](guidelines/10-agent-workflow.md) | planning a multi-step change |
