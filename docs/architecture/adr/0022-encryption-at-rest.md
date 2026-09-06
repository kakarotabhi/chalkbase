# ADR-0022: Encryption at rest for Restricted fields

- Status: Accepted (decisions taken; not yet implemented)
- Date: 2026-09-06
- Deciders: Raja
- Related: [ADR-0014](0014-data-classification.md) (classification), [ADR-0020](0020-student-and-guardian-model.md) (why the columns are missing), [ADR-0015](0015-deployment-baseline.md) (deployment), [ADR-0011](0011-schema-per-tenant.md) (tenancy)

## Context

[ADR-0014](0014-data-classification.md) classifies caste and community, religion, disability and
CWSN status, EWS/BPL/RTE category, guardian income, and APAAR and Aadhaar references as
**Restricted**: encrypted at rest, masked by default in the UI, and every read audited.

None of that machinery exists, which is why
[ADR-0020](0020-student-and-guardian-model.md) §2 leaves those columns out of the student record
entirely rather than store a child's caste in plaintext in a table nobody has decided how to
protect.

**UDISE+ returns require those fields.** So this is not a later refinement; it is on the critical
path to onboarding a real school, and it is the reason the student record is currently incomplete.

## Decisions

### 1. The key comes from the environment, and every ciphertext names which key made it

A 256-bit key supplied as `CHALKBASE_ENCRYPTION_KEY`, read at startup exactly as the database
password already is. AES-GCM, which authenticates as well as encrypts, so a tampered ciphertext
fails to decrypt rather than decrypting to something else.

A managed key service — AWS KMS, Cloud KMS, Vault — is the better isolation and was rejected for
now on grounds of where this actually runs. [ADR-0015](0015-deployment-baseline.md) puts Chalkbase
on **one VPS in Mumbai**. Adopting a cloud provider's key service solely for this would add a paid
external dependency and a network round trip to every restart, to protect against an attacker who,
on a single-box deployment, would already have the machine.

**What makes "environment variable now, key service later" true rather than aspirational is the key
id.** Every ciphertext is stored prefixed:

```
caste_category = "v1:<base64 nonce+ciphertext>"
```

Without that prefix, rotating a key means knowing which rows used which, which nothing records — so
rotation becomes a migration that cannot be done incrementally and therefore never happens. With it,
two keys can be live at once: new writes use the current key, reads accept either, and a background
pass re-encrypts. That is also exactly the shape a move to a key service takes.

The environment variable's honest costs, recorded so they are not discovered later: anyone who can
read the process environment has the key, and that includes a shell on the box, the Coolify UI, and
a heap dump. The key must never appear in a log, an error message, or a stack trace, and
`GlobalExceptionHandler` must not be allowed to serialise a decryption failure's cause.

### 2. Encryption is marked on the entity, and a test binds it to the classification

`@Classification` stays where it is — on the DTO, describing **disclosure**: what may be logged,
shown, or exported. It does not move and it is not duplicated onto entities.

Storage gets its own marker on the entity field:

```java
@Encrypted
@Column(name = "caste_category")
private String casteCategory;
```

And a build-failing test binds the two: **every field whose DTO counterpart is `RESTRICTED` must
carry `@Encrypted`.**

The alternative — repeating `@Classification(Tier.RESTRICTED)` on the entity as well — was rejected
because it states the same fact twice in two places that can disagree. A DTO saying `CONFIDENTIAL`
beside an entity saying `RESTRICTED` is not a compile error, is not visible in review, and is
resolved by whichever one a given piece of code happens to read.

Two facts, stated once each, with a test asserting the relationship between them. This is the same
reasoning that gave the audit log's bulk count a column of its own rather than squeezing a number
into `changed_fields` ([ADR-0018](0018-audit-log.md) §2b): a rule you can satisfy by writing the
same thing in a second place is not a rule.

## Consequences

- **Encrypted columns cannot be searched, sorted, or indexed.** This is a real cost and it lands on
  UDISE+ aggregation: "how many students in each category" cannot be a `group by`. For a school of
  a few thousand it is a decrypt-and-count in a scheduled report, which is fine. A blind index (an
  HMAC of the value, indexed) is the usual escape and is **not** appropriate here — a category with
  ten possible values is brute-forced from its HMAC in ten guesses, so a blind index on caste is
  plaintext with extra steps.
- **A lost key is lost data.** There is no recovery path and there should not be one. The key needs
  a documented backup that is not on the same machine, and that is an operational task, not a code
  one.
- Decryption failure is an error, never a silent null: a row that will not decrypt means the key is
  wrong or the data is corrupt, and both need a person.
- The columns [ADR-0020](0020-student-and-guardian-model.md) §2 left out can land once this does.
  Until then, `ClassificationTests` fails the build if any `RESTRICTED` component appears anywhere,
  so the columns cannot arrive first by accident.
- **Retention still has no answer.** ADR-0014 requires a period per category and Restricted data is
  the category where it matters most. Encryption does not substitute for deletion.
