# ADR-0032: A school carries a time zone — authoritative in the profile, copied to the registry and the session

- Status: Accepted
- Date: 2026-09-08
- Deciders: Raja
- Related: [ADR-0006](0006-configurability-model.md) (configurability tiers), [ADR-0008](0008-frontend-shell-and-session.md)
  (`/api/me` is the hottest call in the application), [ADR-0011](0011-schema-per-tenant.md) (tenancy
  and what is readable before a schema is bound), [ADR-0014](0014-data-classification.md)
  (classification), [ADR-0018](0018-audit-log.md) (the audit log this closes a gap for), [ADR-0029](0029-reference-data.md)
  (the precedent for a URL living under a module's own prefix rather than a shared one)

## Context

`docs/status.md` named the gap under Known gaps and debt:

> A school has no timezone, so the audit screen renders times in the reader's own device zone. India
> is one zone, so this is right for everyone in the country and wrong only for someone reading from
> abroad — the row detail names the zone so they are not misled. A `timezone` on the school closes it
> properly, and is a contract change rather than a screen fix.

Nothing in the schema said which IANA zone a school's dates and times should be rendered in, so
every screen that formats one had to guess, and the only honest guess available client-side was the
reader's own device. That is invisible for a country with one time zone and wrong for anyone
reading a record from outside it — which for an audit log, evidence by definition, is exactly the
wrong screen to be wrong on.

## Where the timezone lives: the profile, the registry, or both

`school_profile` (per tenant) is the school's own editable detail — address, contact, board,
affiliation. `public.school` (the registry) is identity and routing: code, name, schema name, read
**before any tenant is bound**, by the migration orchestrator at startup and by `SchoolLookup.byCode`
resolving which schema a login binds. `module-map.md` already draws this line, and says the registry
keeps a copy of the profile's name, board, city and state "so the platform can list schools without
binding a tenant."

The test for where a new school-level field belongs is the one `module-map.md` implies but does not
spell out: **does anything need the value before a tenant is bound?** Applied here:

1. **Login itself does not.** `AuthenticationService.login` binds the tenant to authenticate the
   account and resolve permissions before the registry value would ever be read, so the schema is
   already open by the time a time zone could matter.
2. **But the session does, on every page load after login.** `SchoolSummary` — the school half of
   `AuthenticatedUser` and `/api/me`'s `MeResponse` — is resolved once, at login, and carried on the
   principal for the rest of the session specifically so that `/api/me` (ADR-0008's hottest call)
   never has to bind a tenant and query `school_profile` just to describe the school on screen.
   `SchoolSummary` today holds `code` and `name`, both registry fields, precisely because the
   registry is what `AuthenticationService` already has open when it builds the session — not the
   profile, which lives in a schema `login` only binds transiently, inside `inSchool(...)`.

A `timezone` that lived only in `school_profile` would need a second, tenant-bound query at login
(or a lazy read on first render) to reach the session — the exact cost ADR-0008 already paid once to
avoid for `code` and `name`. So the answer is the same one `module-map.md` already gives for name,
board, city and state: **authoritative in `school_profile`, copied into `public.school` on every
profile save**, via the same `School.updateRegistryDetails(...)` call the profile service already
makes. `SchoolRef` — the cross-module contract `SchoolLookup.byCode` returns — grows the field so it
rides onto `SchoolSummary` at login, alongside `code` and `name`, with the same staleness contract:
correct as of the last login, corrected at the next one, not by a background refresh. A school that
changes its time zone mid-session does not retroactively repaint an open tab, the same way renaming
the school does not.

## The default: `Asia/Kolkata`, not a required field with no default

Every school this product targets today operates in India, one time zone. Two shapes were possible:

1. **Required, no default.** Correct in the limit, but it makes every school already provisioned
   invalid the moment the migration runs, and asks a principal in Pune to fill in a box whose answer
   is never anything but `Asia/Kolkata`.
2. **Defaulted to `Asia/Kolkata`, editable.** Nobody in India ever has a reason to touch it; a school
   reading its own records from abroad can change it in the same form it already uses for its
   address and contact details.

(2) is what shipped: `not null default 'Asia/Kolkata'` on both the profile column and the registry's
copy, and the same default seeded into a `School` entity's Java field initializer for a school
created before this migration ran or through a path that does not touch `school_profile` directly.
The whole-form `PUT /api/school/profile` still sends `timezone` on every save, the same as `board` or
`pincode` — the form pre-fills it, so filling it in costs nobody a decision, but the contract does
not grow a special "field that need not be sent" case for it.

## Validation: `ZoneId`, not a pattern

A regular expression can reject characters that cannot appear in an IANA zone id. It cannot tell
`Asia/Kolkata` from `Asia/Kalkota` — both look like a zone, only one exists, and a typo that passes a
pattern is accepted silently and renders every timestamp this school reads wrong from that point on,
with nothing in the response to say so. `UpdateSchoolProfileRequest.timezone` carries a new
`@ValidTimeZone` constraint instead, whose validator asks `java.time.ZoneId.of(...)` — the same
authority every formatter on both sides of this change already defers to. The database keeps a loose
`check` constraint on both `school_profile.timezone` and `school.timezone`, mirroring the existing
relationship `ck_school_profile_pincode` has with its own request DTO: a backstop for a write that
reaches the table without going through the API, not the first line of defence, and deliberately not
an attempt to re-implement IANA validity in SQL.

## Which screens use it, and which still use the device zone

**The audit log** (`features/audit/audit-log.ts`) is converted. Both the column's short form and the
detail panel's full form now build their `Intl.DateTimeFormat` with an explicit `timeZone` read from
`SessionStore.schoolTimezone()` — off the session `/api/me` already carried, never a second request.
The detail panel still names the zone out loud (`timeStyle: 'long'`); what changed is whose zone it
is naming. Before this change every reader saw the event in their own device's zone, which read
correctly for everyone in India and misleadingly for anyone reading the log from outside it. Now
every reader — at the school or off it — sees the time the event happened _at the school_, which is
the time worth investigating an incident against, and the zone named in the detail panel is what
lets them tell which clock they are reading.

**The audit log's own date-range filter is not converted, deliberately.** `instantAtStartOfDay` /
`instantAfter` build a "local midnight" from the browser's own `Date` constructor to turn a picked
calendar day into an inclusive instant range. Converting that to "midnight in the school's zone"
needs either the `Temporal` API (not yet available in every browser this product supports) or a
hand-rolled offset calculation good enough to survive a DST transition correctly — real work, for a
narrower payoff than the column above: a reader picks a day off their own calendar, and "the 5th"
read as their own local day is a defensible interpretation of the question being asked, in a way
"14:32" silently meaning the wrong clock is not. Left as a `docs/status.md` line for whoever next
opens this screen, rather than folded into this change's scope.

**No other screen renders a date or time to a user yet** that this pass found — the module map lists
attendance, academics and student records as built, but none of their screens format an instant the
way the audit log does; most render a `LocalDate` (an admission date, a session's start), which has
no time-zone question to answer in the first place. The school-profile form itself gains the
`Time zone` field as a fourth Identity control, so this is where a school actually sets the value the
rest of the product reads.

## An unrelated bug found in the same code, and fixed here because it blocks this work

`SetupKeyFilter` (`platform.config`, `@Profile("prod")`) hides `/api/schools/**` behind a shared
setup key so a stranger cannot reach school onboarding, and answers a missing or wrong key with the
same `NF_002` the application gives for any unmapped address (deliberately — a `401` would confirm
the endpoint exists). `GET /api/schools/boards`, added the day before this change by the
reference-data lane ([ADR-0029](0029-reference-data.md)), lives under that prefix only because
`Board` is `school.domain`'s own type and `platform` must not import it the other way round — it is
not a platform-operator action, requires only `isAuthenticated()`, and discloses nothing about which
schools exist. `SetupKeyFilter`'s matcher does not know that: it guards the whole prefix regardless
of what `@PreAuthorize` says underneath, so on `prod` — the one profile where the filter is even
active, which is why `test`-only CI stayed green — a signed-in school administrator's Board picker
404'd exactly as a stranger's write would have, breaking the school-profile form's Board dropdown on
every deployed environment.

Two shapes were available. **Move the endpoint off `/api/schools/**`** avoids the whole class of
"does the next endpoint under this prefix need to think about the setup key" question, but ADR-0029
explicitly chose this prefix so that "ownership shows through the URL rather than being hidden by
it," and `boards()` is not part of the registry resource `/api/schools` otherwise names — moving it
would need to either supersede that reasoning or invent a third prefix, neither of which this bug
earns. **Name the one exemption in the filter** keeps ADR-0029's placement intact and is narrow: the
filter already documents, endpoint by endpoint, which of `SchoolController`'s methods are
platform-operator actions and which are not, so adding one more named `RequestMatcher` continues a
distinction the class already draws rather than introducing a new one. That is what shipped:
`SetupKeyFilter` gains a `BOARDS_READ` matcher (`GET /api/schools/boards` specifically, not the
whole path) that `shouldNotFilter` treats as exempt, and `SetupKeyFilterTests` — the one file that
actually exercises the `prod` profile — pins both the exemption and its narrowness (the register
itself, and a same-path write, both stay hidden).

Checked while here: no other frontend-reachable endpoint sits under `/api/schools/**`. `SchoolApi`
also calls `list`, `getById` and `create` there, but `SchoolController`'s own class Javadoc already
records that all three require `school:school:create`, a permission no shipped role template holds
— they 403 for every real caller today regardless of this filter, which is documented as intentional
(ADR-0024 rejected a platform-operator account as the larger fix for a smaller problem) rather than
a second instance of this bug. One live endpoint was affected, not a pattern; if a second one ever
is, `SchoolController`'s own Javadoc now says to add its exemption there rather than assume one.

## Consequences

**Easier.** Every audit row is now legible from anywhere the log is read, with no change to how the
gap was already being disclosed (the zone is still named). A school reading its own history from
outside India stops needing to mentally re-derive what a bare "14:32" meant.

**Harder, slightly.** `SchoolSummary`, `SchoolRef` and `School.updateRegistryDetails` all grew a
parameter, which is the ordinary cost of the registry-copy pattern `module-map.md` already accepted
for `board`, `city` and `state` — this is the fourth field to pay it, not a new shape.

**To revisit.** The audit log's date-range filter, once a `Temporal`-shaped API is available broadly
enough to convert "a calendar day" into "midnight in an arbitrary IANA zone" without a hand-rolled
DST calculation. Any future screen that renders an instant to a user should read `SessionStore.schoolTimezone()`
the same way the audit log now does, rather than reintroducing a device-zone assumption one screen at
a time.
