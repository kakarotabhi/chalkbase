# The encryption key

[ADR-0022](../architecture/adr/0022-encryption-at-rest.md) encrypts every Restricted column
(`@Encrypted`, `EncryptedStringConverter`) with a 256-bit key read from `CHALKBASE_ENCRYPTION_KEY`,
exactly the way the datasource password already is. This page is the operational half — generating
it, setting it, and the one property of it that makes losing it different from every other
credential in this project.

## Generate it

```bash
openssl rand -base64 32
```

That is a 256-bit key, base64-encoded, which is the only shape `EncryptionKeyConfiguration` accepts
— anything that does not decode to exactly 32 bytes fails the application at startup with a message
naming the problem.

**Generate a different value than `CHALKBASE_SETUP_KEY`.** They protect unrelated things — this key
protects data at rest, that one protects who can call `POST /api/schools/bootstrap`
([ADR-0024](../architecture/adr/0024-bootstrap-deployment.md)) — and there is no reason for them to
share a value.

## Set it

| Deployment                                                                        | Where                                                                                                                                                                                                   |
| --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Coolify (production, [ADR-0015](../architecture/adr/0015-deployment-baseline.md)) | The application's environment variables, alongside `SPRING_DATASOURCE_PASSWORD` and `CHALKBASE_SETUP_KEY`. See `ops/coolify/.env.example`.                                                              |
| Render (personal dev environment)                                                 | The `chalkbase-api` service's environment, prompted at Blueprint creation (`sync: false` in `render.yaml`) or set later from the service's dashboard.                                                   |
| `local` and `test`                                                                | **Nothing to set.** `EncryptionKeyConfiguration` falls back to a fixed, checked-in development key off the `prod` profile — see its Javadoc for why that is the considered choice and not an oversight. |

The application refuses to start on the `prod` profile if the variable is unset, blank, not valid
base64, or does not decode to 32 bytes. That is deliberate, for the same reason
`SetupKeyConfiguration` refuses to start without `CHALKBASE_SETUP_KEY`: a service that came up
anyway would look healthy on every check while silently storing a child's caste, religion or
disability status in plaintext.

## Back it up. There is no recovery from losing it.

**This is the property that makes this credential different from every other secret in this
project.** A lost database password is replaced by resetting one on the database. A lost
`CHALKBASE_SETUP_KEY` is replaced by generating a new one. A lost `CHALKBASE_ENCRYPTION_KEY` is lost
_data_ — every Restricted value ever written becomes permanently unreadable, and
`EncryptedStringConverter` is built to make that loud (`DecryptionFailedException`, ADR-0022's
"never a silent null") rather than to paper over it.

There is deliberately no runbook entry here for "restore the encryption key" — there is no such
operation. What belongs here instead, before the first Restricted column exists in a database that
matters:

- [ ] A copy of the current key, encrypted or held somewhere access-controlled that is **not** the
      VPS or the Render service it protects data on — a password manager entry or an offline copy,
      not a file next to the database backup it is meant to survive the loss of.
- [ ] A written note of who can retrieve it and how, so that person is known before an incident,
      not during one.
- [ ] The same two items again for whatever key rotation eventually adds as a second, previous key
      id — a rotation is only really finished once the key it retired is backed up too, for exactly
      as long as any row still carries its id.

This is also on the list in [`docs/operations/README.md`](README.md)'s runbooks-to-write-before-a-
real-school-goes-live; it is called out here on its own because "back up the encryption key" is easy
to read as routine advice and it is not routine — it is the one credential in this project a restore
procedure cannot help with.
