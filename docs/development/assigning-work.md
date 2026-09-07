# Assigning a piece of work

For the person handing a task to someone — an agent or a new developer. It is a route, not a
process: four files in order, then the five things a brief has to say that this repository will not
tell the worker by itself.

[10-agent-workflow.md](../ai/guidelines/10-agent-workflow.md) is the other half, written for whoever
receives the work.

## The four files, in this order

| Step | File | What you get from it |
|---|---|---|
| 1. Pick the work | [status.md](../status.md) | **What to do next** is the ranked queue. **Phase 1 in detail** is what is really built — three features read as done in the summary table and are not, so read that section before you scope anything. |
| 2. Check it can run now | [parallel-work.md](parallel-work.md) | Whether it collides with work already running, and what it will contend on. Some items must run alone. |
| 3. Find the owner | [module-map.md](../architecture/module-map.md) | Which module owns the tables and endpoints. One module per agent is the rule that makes the rest work. |
| 4. Rules for the work | [`AGENTS.md`](../../AGENTS.md), `backend/AGENTS.md`, `frontend/AGENTS.md`, [guidelines](../ai/guidelines/) | Loaded automatically by most tools. You do not need to repeat them in a brief. |

If the work touches an endpoint, add [contracts/README.md](../../contracts/README.md) — the contract
is generated and committed, and the nullable-response case has a trap in it.

## What a brief must say

Everything above is already written down. These five are not, and each one has cost real time here.

1. **Which module, and what it must not touch.** The module map gives the boundary; the brief has to
   name it. "Add subjects" invites an agent into `student` to wire up marks.
2. **The collision list from [parallel-work.md](parallel-work.md)**, if anything else is running.
   Naming the file that will conflict turns a surprise into a three-line resolve.
3. **Do not run the full test suite locally.** This machine OOM-kills concurrent builds, and a
   ten-file `ng test` run produced 47 spurious 5000 ms timeouts in files that pass individually.
   Targeted tests locally; push and read the Actions run. CI is the signal.
4. **A fresh worktree has no `node_modules`.** A frontend task starts with `npm ci`, and the failure
   without it looks like a broken checkout rather than a missing install.
5. **Timestamp a migration when you merge it, not when you start it.** `outOfOrder` is off, so an
   older version than one a database has already applied is refused — and it passes CI, which starts
   from an empty container, then fails on the shared dev database and on Render.

## Verify the brief before you send it

Two habits, both from mistakes made here rather than from principle.

- **Check ADR numbers resolve.** A document written in this session cited ADR-0013 for file storage
  and ADR-0007 for contract generation. ADR-0013 is payments and messaging; ADR-0007 is the response
  envelope; neither subject has an ADR at all. A brief that cites the wrong decision record sends an
  agent to read the wrong argument. `ls docs/architecture/adr/` takes a second.
- **Check the claim, not the comment.** Three comments in the frontend asserted the shell painted
  its chrome while `/api/me` was in flight. Nothing had ever been built to do it, and each comment
  cited a different mechanism, so reading any one made the other two look corroborated. If a brief
  rests on behaviour a comment describes, open the code.

## Work that is not a task yet

Some things in [status.md](../status.md) cannot be assigned, because a decision is missing rather
than effort:

- **Documents** — there is no ADR for file storage at all, and no storage port in the code.
  Certificates and compliance records ([FR-013](../requirements/02-functional-requirements.md)) have
  nowhere to put a file.
- **Audit retention** — ADR-0014 requires a period per category. The number is a legal question.
- **`.xlsx` import** — needs Apache POI, which AGENTS rule 8 says to ask about before adding.

Assigning these produces an agent inventing the decision, which is the expensive kind of rework.
