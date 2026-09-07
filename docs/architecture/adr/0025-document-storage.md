# ADR-0025: Documents are a storage port, an S3-compatible adapter, key-prefixed by tenant

- Status: Accepted
- Date: 2026-09-07
- Deciders: Raja
- Related: [ADR-0013](0013-external-provider-ports.md), [ADR-0011](0011-schema-per-tenant.md),
  [ADR-0014](0014-data-classification.md), [ADR-0015](0015-deployment-baseline.md),
  [ADR-0020](0020-student-and-guardian-model.md), [ADR-0022](0022-encryption-at-rest.md)

## Context

[FR-013](../requirements/02-functional-requirements.md) and
[FR-032](../requirements/02-functional-requirements.md) need certificate and compliance documents,
and a student's photo, signature and documents, with a verification status. Nothing here has ever
had anywhere to put its bytes: `docs/status.md` has carried this as "not started, unblocked" once a
storage decision existed, because a database column is not a place to put a scanned birth
certificate and nothing in the product could say where else one would go.

The product owner has now decided the shape: **a storage port, in the ADR-0013 style, with an
S3-compatible adapter, and Supabase Storage as the development target.** Supabase Storage speaks the
S3 API, so the same adapter that runs in development points at a different endpoint in production —
that is the reason it was chosen over a bespoke API. This ADR records what follows from that: where
the port lives, how a school's files are kept apart from another school's, how a document is
downloaded, what is authoritative when the database and the object store disagree, and what is
deliberately not built yet.

This also revises part of [ADR-0015](0015-deployment-baseline.md), which named MinIO with one bucket
per school as the production plan. That plan is superseded by this one — see
[Superseding ADR-0015's storage section](#superseding-adr-0015s-storage-section) below.

## Options considered

### Where the port lives

1. **Inside the new `document` module.** Keeps the module self-contained, but a storage port is not
   a fact about documents — staff records, admission attachments and compliance exports will all
   want somewhere to put a file, and putting the port behind one feature module's boundary means
   every later consumer either imports `document.api` for an unrelated reason or the port gets
   moved later, at which point every caller's import changes. Rejected.
2. **In `platform`, next to `platform.crypto`.** `platform` is the OPEN shared kernel and already
   holds exactly this shape of thing: `EncryptionKeyConfiguration` wires a key from the environment,
   profile-gated, with `local`/`test` given a safe fallback and `prod` refusing to start (or, here,
   refusing to serve — see below) without real configuration. A storage port has no school domain
   meaning of its own, which is precisely `platform`'s admission criterion.

### Decision: option 2. `platform.storage.StorageService` is the port; `document` is its first caller.

## Decision

### The port

```java
public interface StorageService {
    StoredObject store(String relativeKey, byte[] content, String contentType);
    byte[] retrieve(String relativeKey);
    void delete(String relativeKey);
}
```

`relativeKey` is **tenant-relative** — the caller never names a school, a bucket or a prefix. The
adapter is what turns a relative key into wherever the bytes actually live, the same way
`SchemaMultiTenantConnectionProvider` turns "the current tenant" into a `search_path` without a
caller ever naming a schema. This is the enforcement mechanism for the next decision.

### Tenancy: one shared bucket, key-prefixed by schema, prepended by the adapter alone

[ADR-0015](0015-deployment-baseline.md) said "one bucket per school", written for a self-hosted
MinIO the product owner fully controls. That premise no longer holds: the target is now Supabase
Storage in development and, per ADR-0015's own "to revisit" note about moving storage off-box,
Cloudflare R2 or Backblaze B2 in production — three different providers this product does not run,
each with its own bucket-count ceiling and its own bucket-creation friction, and provisioning a
bucket at every school onboarding would add a second external call, on a second provider, to a
sequence ([ADR-0011](0011-schema-per-tenant.md)'s `CREATE SCHEMA`) that is deliberately simple today.

The alternative, and the one this ADR takes, is a **single bucket per environment, with every key
prefixed by the tenant's schema name** — `<schema>/documents/<object-id>.<ext>`. This is the same
trade [ADR-0011](0011-schema-per-tenant.md) rejected for the _database_: a boundary living in a
value that every call site must remember to supply is exactly the row-level tenancy that schema-per-
tenant exists to avoid repeating. The difference here is where the enforcement sits: **the prefix is
never supplied by a call site.** `document.application.DocumentService` calls
`storage.store("documents/" + objectId + ".pdf", ...)` and never sees a schema name at all;
`platform.storage`'s adapter reads `TenantContext.currentSchema()` — the same source
`SchemaMultiTenantConnectionProvider` reads for the database connection — and prepends it before any
byte reaches the object store, and strips it on the way back. A module that wanted to leak another
school's prefix would have to import `platform.tenancy.TenantContext` and construct the crossing
deliberately; it cannot happen by omission, which is the property that matters. This is why the
`document` table's own `storage_key` column holds only the relative half: it is redundant with, and
would drift from, the schema the row already lives in.

**What this costs**, honestly: unlike schema-per-tenant, this is not a boundary the object store
itself refuses to cross — a bug in the one adapter class is a cross-school leak, where a bug in
`SchemaMultiTenantConnectionProvider` is bounded the same way. That single class is therefore the
highest-value thing to review carefully and to cover with the negative test `backend/AGENTS.md`
already requires of every tenant-scoped module: tenant A's key must not resolve to tenant B's object
even when both exist.

**What this buys**, against bucket-per-school: onboarding stays a database-only operation; the
approach is identical across Supabase Storage, R2, B2 and, if it is ever self-hosted again, MinIO;
and it does not run into a provider's bucket-count ceiling as schools are added — schema-per-tenant
already has its own documented ceiling (ADR-0011: "hundreds of schemas is comfortable, thousands is
not"), and there is no reason to add a second, uncoordinated ceiling on top of it from a different
provider's bucket limits.

**To revisit.** If per-school storage isolation ever becomes a compliance requirement stronger than
"the application enforces it correctly" — a school's contract demanding its files sit in storage
nothing else can reach even in principle, or bulk erasure needing to be one bucket-delete rather than
a listed, batched, delete-by-prefix — this becomes bucket-per-school without changing
`document`'s code at all, only the adapter's key construction. That is the point of the port.

### Superseding ADR-0015's storage section

ADR-0015's "Storage" section (MinIO, one bucket per school, pre-signed URLs) is superseded by this
ADR's tenancy decision above and the download decision below. Its other content — object storage
being on the same box initially, and being moved off-box before the disk fills — is unaffected.

### The download path: the application proxies every byte; there is no signed URL

A document about a child defaults to Confidential under [ADR-0014](0014-data-classification.md),
and this ADR's own choice of document types (below) keeps every one of them at Confidential rather
than Restricted. Confidential's rule is "permission-gated... export is audited", which a signed URL
satisfies only at the moment it is issued — after that it is a bearer token, valid for whoever holds
it, until it expires, checked against nothing: not the session that requested it, not whether that
session has since been revoked ([ADR-0023](0023-session-revalidation.md) exists precisely because a
session can go bad mid-life), not whether the account still holds the permission. A URL that leaks
into a browser's history, a proxy access log, or a screenshot works for anyone who finds it, for as
long as it has left to live.

**Decision: every document download is proxied through the application.**
`GET /api/documents/{id}/content` runs the same `@PreAuthorize` check as every other endpoint, on
every request, reads the bytes from the object store, and streams them back with the stored content
type and filename. No signed URL is ever issued, no storage endpoint, bucket or key is ever visible
to the browser, and a revoked session or a permission removed five minutes ago is refused on the very
next download attempt — exactly the guarantee the product already gives session cookies and never
gives out for anything the way a signed URL would. Each download is audited as an export
(`AuditAction.DATA_EXPORTED`), on the same reasoning ADR-0014 gives for masked exports: a document
leaving the application into a downloads folder or an email attachment is the moment protected data
left the building.

**What this costs.** Every byte is read out of the object store and re-written through the JVM
rather than served by the storage provider's own edge; there is no CDN-style delivery. At the sizes
this ADR caps documents to (below), and at one pilot school's traffic, this is not a real cost yet
— virtual threads are already on, and the number is worth re-measuring before it is dismissed at ten
schools rather than one. **To revisit** if a low-sensitivity, genuinely public artifact is ever
served this way — the certificate verification response ADR-0014 already names as Public tier is
exactly such a case, and a future public verification endpoint is a different, deliberately public,
code path, not a reason to weaken this one.

### Document types: excluding Restricted categories is a decision, not an oversight

The obvious catalogue for FR-013/FR-032 includes a caste certificate, an Aadhaar copy, a disability
certificate. Each of those is excluded from `DocumentType` in this build, deliberately: the _type_
of a document is itself Restricted-category information under ADR-0014 — a row saying a child has a
`CASTE_CERTIFICATE` on file discloses the same fact a `caste` column would — and
[ADR-0020 §2](0020-student-and-guardian-model.md) already left every Restricted student column out
of the schema until encryption at rest, masking and read-auditing exist for them
([ADR-0022](0022-encryption-at-rest.md) built the mechanism; the columns themselves are still
pending). Shipping a `DocumentType` value whose mere presence on a row reveals the same category
would reopen exactly the gap ADR-0020 §2 closed, one layer up.

Phase 1's catalogue is therefore: `BIRTH_CERTIFICATE`, `TRANSFER_CERTIFICATE`, `REPORT_CARD`,
`PHOTO`, `SIGNATURE`, `OTHER` — every one Confidential, none Restricted. `OTHER` exists for whatever
a school needs to attach that is not one of the named kinds, and remains Confidential; a school
attaching something Restricted under `OTHER` is not a case this build can prevent by type, and is the
same trust boundary the record's own `@PreAuthorize` already draws. The Restricted-category document
types are **not** a "later" placeholder in this enum; they need the same encryption-and-masking
machinery the student record's own Restricted columns are waiting on, and land together.

### What is authoritative, and which way a failure is allowed to point

**The database row is authoritative.** It is what every permission check, every audit entry and
every screen consults; the object store holds bytes a row points at and nothing more. This settles
the ordering for both halves of the lifecycle, by one rule: **mutate the object store first, then
the database row — for create and for delete alike** — because that is the ordering under which an
inconsistency always shows up as _"the row claims something the bytes cannot back up"_, a state a
retry or a plain download error immediately reveals, and never as _"bytes survive with no row
pointing at them, permission-checking them, or accounting for their existence"_, a state nothing in
the application will ever surface on its own — and, under DPDP, the one kind of inconsistency that
is a compliance failure rather than a bug report.

- **Upload**: write bytes, then insert the row. If the row insert fails after a successful write, the
  result is an orphaned object nobody can reach — it has no row, so no permission check, no listing
  and no download path ever finds it. Wasted space, not exposed data.
- **Delete**: delete bytes, then delete the row, in one transaction. If the byte-delete throws, the
  transaction never reaches the row and rolls back whole — nothing changes, and the caller sees the
  failure and can retry. If the row-delete somehow fails after the bytes are gone (it should not,
  barring a concurrent modification), the row survives pointing at nothing, which a subsequent
  download reports honestly as a storage error rather than silently serving nothing.

**Not built: reconciliation.** A scheduled job listing each tenant's key prefix and diffing it
against `document.storage_key` rows — reporting orphaned objects and dangling rows rather than
auto-repairing either — would close the remaining gap. It needs the same scheduler infrastructure
this product does not have yet for anything else (the renewal reminder below, and every notification
in [ADR-0013](0013-external-provider-ports.md)), so it is deferred for the same reason, not a
separate one.

### Renewal reminders: the date is modelled, the reminder is not

FR-013 asks for issue date, expiry date and renewal reminders. `document.expiry_date` exists,
nullable — a document with no expiry (a photo, a birth certificate) simply never carries one. A
reminder needs a scheduler to notice an approaching expiry and a channel to tell someone, and
neither exists: [ADR-0013](0013-external-provider-ports.md) registered email and web push as
`NotificationChannel` adapters but nothing yet calls them on a schedule rather than inline with a
request. Building the reminder now would mean inventing a scheduler for one feature rather than as
its own decision. **Deferred, not forgotten** — the column it needs already exists.

### Verification status

`document.verification_status` — `UNVERIFIED` (the default), `VERIFIED`, `REJECTED` — answers
FR-032's "verification status" directly. Phase 1 gates changing it behind the same
`document:document:manage` permission as everything else about a document; a school that wants
upload and verification to be different people's jobs is exactly the kind of split
[ADR-0005](0005-authorization-model.md)'s per-permission model exists to let a school configure for
itself later, not something this build should invent a third permission to force today.

### Credentials and profiles

The S3-compatible adapter takes an endpoint, an access key and a secret from the environment, never
committed, following `EncryptionKeyConfiguration` and `SetupKeyConfiguration`'s existing pattern
exactly. What differs from those two is what happens when configuration is absent, and it differs on
purpose:

- **`local` and `test`** get a `FilesystemStorageService` — a real adapter, not a stub, writing under
  the OS temp directory. It satisfies the same port every other adapter does, so `document`'s tests
  need no real bucket, the same way `EncryptionKeyConfiguration`'s fixed development key means no
  developer mints one before `./mvnw spring-boot:run` works.
- **`prod`** gets an `UnavailableStorageService` that throws a mapped, loud
  `ObjectStoreUnavailableException` on every call, rather than either of the two worse options: a
  hard boot refusal (which would take down the entire application — including every feature that has
  nothing to do with documents — on every deployment until someone configures a bucket, and Render's
  live dev deployment would go down the moment this merges) or silently falling back to the
  filesystem adapter in a container whose disk does not survive a redeploy, which would accept
  uploads that later vanish without a trace, the exact silent failure this codebase refuses
  everywhere else. `prod` therefore boots and serves every other feature normally; every document
  endpoint answers a clear 503 until the adapter below is approved and configured.

## The dependency question, asked rather than assumed

An S3-compatible adapter needs to sign requests (SigV4) and speak the S3 XML/REST API. Two ways to
get there, and `AGENTS.md` rule 8 says this is not this build's call to make alone:

1. **AWS SDK v2, `software.amazon.awssdk:s3`.** Well-maintained, handles SigV4, retries, path- vs
   virtual-hosted addressing (Supabase Storage and most self-hosted S3-compatible targets need
   path-style) and multipart uploads for free. Cost: a large dependency (the S3 module alone pulls in
   its own HTTP client, region metadata and credential-provider chain machinery) for a product that
   needs perhaps a dozen S3 operations, and a CVE surface that scales with all of it rather than with
   what is actually used.
2. **A hand-rolled SigV4 signer**, in the shape `CsvReader` already set the precedent for (`AGENTS.md`
   rule 8's "ask before adding a dependency" is exactly why that CSV parser is a hundred hand-written
   lines rather than a library). SigV4 for a handful of operations (PUT, GET, DELETE, no multipart
   upload needed under an 8 MB cap — see below) is a known, bounded algorithm rather than an open-
   ended one, and it exists as a small class with no third-party dependency and nothing to patch.
   Cost: it is code this team now owns and maintains, and a subtle signing bug fails as an
   authentication error against the bucket rather than as a compile error.

**This ADR does not choose.** The port and the `local`/`test` filesystem adapter are built and
covered by the module's tests; `prod` deliberately serves a clear "not configured" answer instead of
either dependency. The product owner's answer determines what `UnavailableStorageService` is
replaced with, and needs nothing else in this module to change — that is the port doing its job.

## Consequences

**Easier.** A school's photo, signature and certificates finally have somewhere to live, with the
metadata half — type, dates, verification — fully queryable and audited from day one. Moving from
Supabase Storage in development to Cloudflare R2 or Backblaze B2 in production is a configuration
change, not a code change, which was the entire point of choosing an S3-speaking dev target. The
`local`/`test` filesystem adapter means every test in `document` runs with no external service.

**Harder.** The tenancy boundary for files is enforced by one adapter class rather than by the
database itself, which is a weaker guarantee than schema-per-tenant and needs to be treated that way
in review and in testing. `prod` cannot actually serve a document until the dependency above is
decided and configured — this module ships inert on the one environment that matters, by design,
rather than half-built.

**To revisit.** The dependency decision, above. Bucket-per-school, if isolation requirements
outgrow a shared bucket with an enforced prefix. Reconciliation, once there is a scheduler to run it
on. The renewal reminder, once `NotificationChannel` has a caller that runs on a schedule rather than
inline with a request. Restricted-category document types, together with the student columns
ADR-0020 §2 is also waiting on.
