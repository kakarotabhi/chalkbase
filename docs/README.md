# Chalkbase documentation

**Start here for state:** [status.md](status.md) — what is done, what is next, what is waiting on a
decision. Updated in the same pull request as the work.

| Folder | What lives here | Who reads it |
|---|---|---|
| [requirements/](requirements/README.md) | What we are building and why. Scope source of truth. | Everyone |
| [architecture/](architecture/overview.md) | How the system is put together, plus decision records. | Developers, agents |
| [development/](development/README.md) | Getting set up, coding standards, workflow. | Developers |
| [api/](api/README.md) | API conventions and the generated reference. | Frontend, integrators |
| [operations/](operations/README.md) | Deploy, backup, monitoring, incident runbooks. | Whoever is on call |
| [manual/](manual/README.md) | End-user help, per role. | School staff, parents |
| [compliance/](compliance/README.md) | UDISE+, APAAR, DPDP evidence and mappings. | Auditors, management |
| [ai/guidelines/](ai/guidelines/) | Deep stack reference for AI agents. | Agents |

## Keeping docs alive

The rule is in the root `AGENTS.md` and worth repeating: documentation changes in the same commit as
the code.

- User-visible behaviour changed → update the relevant page in `manual/`.
- A structural or irreversible choice was made → add an ADR in `architecture/adr/`.
- A module was added or its ownership changed → update `architecture/module-map.md`.
- An endpoint changed → the OpenAPI spec regenerates; note breaking changes in `api/`.

Every page is plain Markdown with a stable path, so the manual can be published as a docs site
(MkDocs Material or Docusaurus) and linked from in-app help without moving anything.
