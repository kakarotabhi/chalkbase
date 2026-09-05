# ADR-0008: Navigation comes from the server

- Status: Accepted
- Date: 2026-09-05
- Deciders: Raja
- Related: [ADR-0005](0005-authorization-model.md) (permissions), [ADR-0006](0006-configurability-model.md) (configurability)

## Context

The menu a user sees depends on three things the **server** owns and the frontend cannot know:

1. **Which permissions the user holds** — and roles are per-school editable data (ADR-0005), so a
   school can invent "Fee Counter Operator, evening shift" without a release.
2. **Which modules that school uses** — a school with no hostel should not see Hostel (ADR-0006).
3. **What the school calls things** — some schools say "Fees", others "Fees & Dues".

If the frontend hardcodes the menu, it has to re-derive "who can see what" from the permission list,
which means the authorization model exists in two places. They will drift, and the drift shows up as
either a menu item that 403s when clicked, or a feature a school paid for that nobody can find.

## Decision

**The server returns the navigation tree after login**, as part of a single bootstrap call.

```
GET /api/v1/me
→ { user, school, permissions[], navigation[], settings }
```

One call rather than five, because the alternative is a request waterfall on every page load and
these are the same low-end tablets on a school's broadband.

Each navigation node is:

```jsonc
{
  "id": "fees.collect",        // stable; the frontend maps this to a route it owns
  "labelKey": "nav.fees.collect",
  "label": "Fee & Dues",       // present only when the school overrode the default
  "icon": "receipt",
  "order": 30,
  "children": []
}
```

### What the server decides, and what it does not

| Server owns | Frontend owns |
|---|---|
| Which items exist for this user | What a route actually is |
| Their order and nesting | How anything is rendered |
| A label override when a school set one | Icons, spacing, responsive behaviour, animation |

**The server never sends a URL, a component name, or anything visual.** It sends a stable `id`; the
frontend maps ids to its own lazy routes. So a backend deploy cannot break navigation by referencing
a route that does not exist, and the frontend cannot show an item the server did not authorise. An
unknown id is dropped and logged rather than rendered as a dead link.

This is the line between server-driven *navigation* and server-driven *UI*. The second is rejected:
shipping layout or component descriptions over HTTP produces a UI framework nobody can debug,
type-check or test, and puts pixel decisions in the backend.

### Labels are keys, not sentences

`labelKey` is translated by the frontend. Parent portals will need Hindi and regional languages;
sending display strings from the backend would drag every translation into Java. The optional
`label` exists only for a school's own renaming, which is Tier-2 configuration (ADR-0006) and is
genuinely per-school data rather than a translation.

### Menus are not security

**Hiding a menu item is a convenience, never an authorization control.** Every endpoint enforces its
own permission (ADR-0005), and that enforcement is what protects data. A hidden item with an
unguarded endpoint is the oldest bug in this category — anyone can type the URL.

Corollary: it is fine for navigation to be slightly generous. It is not fine for an endpoint to be.

### Staleness

Effective permissions are computed once per session (ADR-0005), so a role change mid-session leaves
a stale menu. Handling:

- The bootstrap response carries a `permissionsVersion`.
- Any `403` from the API is treated by the frontend as a signal that its view is stale: refetch
  `/api/v1/me`, re-render navigation, then show the error.

That converts the confusing case ("the button is there but it fails") into a self-correcting one.

## Consequences

- Adding a module means adding its navigation ids to the frontend's route map. That is a deliberate
  cost: it keeps routing type-checked and prevents a backend change from producing dead links.
- The navigation contract needs a test on both sides — the backend must not emit an id the frontend
  cannot resolve. A test enumerating the frontend's known ids against the backend's catalogue is the
  cheap way to catch it, and belongs with the identity module.
- `/api/v1/me` becomes a hot path and the first thing to break the app if it is slow. It is served
  from the session's cached permissions, not recomputed per call.
