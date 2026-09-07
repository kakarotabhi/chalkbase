# Document storage (the S3-compatible adapter)

[ADR-0025](../architecture/adr/0025-document-storage.md) put a student's certificates, photo and
other documents behind `platform.storage.StorageService`, with Supabase Storage as the S3-compatible
target — reached over its S3 API, in one shared bucket per environment, every key prefixed by the
tenant's schema. This page is the operational half: the five environment variables the adapter
needs, exactly where each value comes from in the Supabase dashboard, and what happens when one is
missing.

## The five variables

| Variable                       | What it is                                                                | `local` / `test`                                   |
| ------------------------------ | ------------------------------------------------------------------------- | -------------------------------------------------- |
| `CHALKBASE_STORAGE_ENDPOINT`   | The S3-compatible endpoint for this Supabase project's Storage.           | Not read — the filesystem adapter is used instead. |
| `CHALKBASE_STORAGE_REGION`     | The region string Supabase's S3 endpoint expects on every signed request. | Not read.                                          |
| `CHALKBASE_STORAGE_BUCKET`     | The one bucket every school's documents share, key-prefixed by schema.    | Not read.                                          |
| `CHALKBASE_STORAGE_ACCESS_KEY` | The access key id for an S3 access key created for this project.          | Not read.                                          |
| `CHALKBASE_STORAGE_SECRET_KEY` | The secret for that same S3 access key.                                   | Not read.                                          |

**Unlike `CHALKBASE_ENCRYPTION_KEY`, none of these stop the application starting when they are
absent.** `StorageConfiguration` falls back to `UnavailableStorageService` on the `prod` profile if
even one of the five is unset or blank, and the deployment boots normally with every other feature
working — every request under `/api/documents/**` answers a clean, mapped 503 until all five are
set. [ADR-0025](../architecture/adr/0025-document-storage.md#credentials-and-profiles) explains why
that is the considered choice: a Restricted column can be written the moment any code reaches it, so
encryption cannot come up half-configured, but nothing uploads a document until this module's own
endpoints are called.

## Where to find each value in the Supabase dashboard

1. **Endpoint and region** — open the project, then **Project Settings → Data API**. The **S3
   Connection** panel on that page states the endpoint directly
   (`https://<project-ref>.supabase.co/storage/v1/s3`) and the region the project runs in (for
   example `ap-south-1`). Copy both exactly — the endpoint is used with **path-style addressing**
   (`<endpoint>/<bucket>/<key>`), not the virtual-host style AWS's own S3 serves
   (`<bucket>.<endpoint>/<key>`), which Supabase Storage does not answer.
2. **Bucket** — **Storage** in the left sidebar, then create (or reuse) a bucket for this
   environment. **Private, not public** — every download is proxied and permission-checked by this
   application (ADR-0025); a public bucket would let anyone who guesses or leaks a key fetch a
   document straight from Supabase, bypassing every check this product makes. The bucket name is
   what goes in `CHALKBASE_STORAGE_BUCKET`, exactly as created.
3. **Access key and secret** — still under **Storage**, open **S3 Access Keys** (a project-level
   setting, separate from the `anon` and `service_role` API keys used elsewhere) and create a new
   key pair scoped to this bucket. The secret is shown once, at creation — copy it immediately into
   wherever the deployment's environment variables are set; Supabase cannot show it again, only let
   you revoke it and create another.

## Set it

| Deployment                                                                        | Where                                                                                                                                                                                                                                       |
| --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Render (`chalkbase-api`, the dev environment)                                     | The service's environment variables — `sync: false` in `render.yaml`, prompted at Blueprint creation or set later from the service's dashboard. Points at the same Supabase project the datasource already uses.                            |
| Render (`chalkbase-api-staging`)                                                  | The same five variables, `sync: false`, but pointed at a bucket and access key in the **second** Supabase project staging's datasource already uses — never the dev environment's bucket, for the same reason staging has its own database. |
| Coolify (production, [ADR-0015](../architecture/adr/0015-deployment-baseline.md)) | The application's environment variables, alongside `SPRING_DATASOURCE_PASSWORD`, `CHALKBASE_SETUP_KEY` and `CHALKBASE_ENCRYPTION_KEY`. See `ops/coolify/.env.example`.                                                                      |
| `local` and `test`                                                                | **Nothing to set.** Both profiles get `FilesystemStorageService`, a real adapter writing under the OS temp directory — no bucket, no network, no credentials.                                                                               |

## Checking it worked

There is no dedicated health check for storage — `/actuator/health` does not probe the bucket, and
building the S3 client makes no network call at startup, so a wrong `CHALKBASE_STORAGE_SECRET_KEY`
looks identical to a correctly configured one until the first request. The way to check is to try
the feature: sign in, open a student record, upload a document. A working adapter answers with the
new document's metadata; a misconfigured one answers `DOC`-prefixed success paths normally and fails
the upload with the same mapped 503 (`ObjectStoreUnavailableException`) the unconfigured state
answers with, which is indistinguishable from "not configured yet" by design — see ADR-0025 for why
neither case is treated as more actionable than the other from inside the request.
