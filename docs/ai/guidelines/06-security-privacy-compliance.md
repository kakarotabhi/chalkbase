# Security, Privacy, and Compliance Guidelines

## Security Posture

This system handles children's personal data, financial records, school compliance records, payroll, health information, counselling records, and identity-related fields. Treat security and privacy as core functionality.

Use these defaults:

- Least privilege.
- Deny by default.
- Server-side authorization.
- Object-level access checks.
- Sensitive field masking.
- Audit logging.
- Explicit consent where required.
- Secure-by-default deployment.

## Authorization Rules

Authorization must cover:

- Function access: can this role perform this action?
- Object access: can this user access this specific record?
- Scope access: is this within the user's school, campus, class, section, subject, route, hostel, or department?
- Field access: can this user view or edit sensitive fields?
- State access: is the record locked, published, cancelled, or archived?

Do not rely on only role names like `ADMIN`. Model permissions.

Examples:

- A subject teacher can view students in assigned subjects, not all students.
- A class teacher may get broader access for assigned class-section.
- An accountant can see fee records but not counselling notes.
- A nurse can see health data but not fee concessions.
- A parent can see only linked children.
- A transport user can see pickup/drop data for assigned route only.

## Authentication Rules

- Use strong password hashing.
- Rate-limit login and OTP attempts.
- Lock or throttle suspicious accounts.
- Support MFA for administrators and finance users.
- Support session revocation.
- Do not expose whether a phone/email exists during password reset.
- Store OTPs hashed or encrypted with expiry.
- Never log passwords, OTPs, reset tokens, or session tokens.

## Sensitive Field Classification

Classify fields as:

- Public.
- Internal.
- Confidential.
- Sensitive.
- Restricted.

Restricted fields include:

- APAAR ID, PEN, board registration where sensitive.
- Aadhaar-related fields if ever collected.
- Caste/community, religion, minority, RTE/EWS/BPL, income.
- Disability/CWSN, medical, counselling, safeguarding.
- Bank, payroll, payment gateway details.
- Guardian identity documents.

Restricted fields require explicit permissions and audit logging.

## Child Data and Consent

Requirements:

- Record consent for optional processing.
- Track parent/guardian who gave consent.
- Track consent text/version.
- Track purpose, date, channel, and evidence.
- Support refusal and withdrawal.
- Do not make optional identifiers mandatory by default.
- Avoid collecting data that is not needed for a configured workflow.

APAAR-related processing must include consent tracking and restricted access.

## DPDP-Aware Engineering

The Digital Personal Data Protection Act, 2023 creates strong expectations around personal data processing. Engineering controls should support:

- Purpose limitation.
- Data minimization.
- Notice and consent records.
- Correction workflow.
- Access/export workflow where required.
- Retention policy.
- Deletion or archival process.
- Breach incident tracking.
- Processor/sub-processor inventory.

Do not add analytics, tracking, or third-party scripts that process personal data without documenting purpose and consent/legal basis.

## Audit Logging

Audit logs are required for:

- Authentication events.
- Authorization failures for sensitive resources.
- User/role/permission changes.
- Student sensitive data changes.
- Guardian contact changes.
- Fee transactions and reversals.
- Concessions and approvals.
- Attendance corrections.
- Exam marks locking and publishing.
- Certificate generation and reprint.
- Sensitive file access.
- Data exports.
- Consent changes.
- Public disclosure publishing.
- Configuration changes.

Audit logs should include:

- Actor.
- Action.
- Entity type and ID.
- School/tenant context.
- Timestamp.
- Request ID.
- IP/user agent where available.
- Before/after values for important fields.
- Reason/comment for high-risk actions.

Normal users must not be able to edit or delete audit logs.

## Logging Rules

Application logs must not contain:

- Passwords.
- OTPs.
- Tokens.
- Payment secrets.
- Full identity numbers.
- Health/counselling/safeguarding notes.
- Full bank account data.
- Uploaded document contents.

Logs should contain:

- Request ID.
- Safe user ID.
- Safe school ID.
- Operation name.
- Result.
- Error code.
- Provider correlation ID where safe.

## File Upload Security

Rules:

- Allow only approved file types.
- Enforce size limits.
- Store files outside the web root.
- Use private object storage.
- Generate short-lived signed download URLs.
- Scan files if scanner integration exists.
- Log access to sensitive files.
- Do not trust client-provided MIME type.
- Do not execute uploaded files.

## Payment Security

Rules:

- Verify payment gateway signatures.
- Process webhooks idempotently.
- Never trust frontend payment success alone.
- Generate receipts only after server-side verification.
- Store only necessary payment metadata.
- Do not store card data.
- Reconcile settlement data.
- Log payment state transitions.
- Alert on mismatches.

## API Security

Follow OWASP API Security principles:

- Prevent broken object-level authorization.
- Prevent broken authentication.
- Prevent excessive data exposure.
- Prevent unrestricted resource consumption.
- Prevent broken function-level authorization.
- Protect sensitive business flows.
- Prevent SSRF in webhook/file/import integrations.
- Avoid security misconfiguration.
- Maintain API inventory.
- Validate third-party API consumption.

## Deployment Security

Production must use:

- HTTPS.
- Secure cookies.
- Restricted CORS.
- Strict security headers where compatible.
- Secrets through environment variables or secret management.
- Non-root containers where practical.
- Minimal exposed ports.
- Database not publicly exposed.
- Object storage not publicly exposed unless intentionally serving public assets.
- Regular backups.
- Restore testing.
- Dependency updates.

## Compliance Data

For CBSE, UDISE+, APAAR, RTE, and state-board workflows:

- Store source data cleanly.
- Store export snapshots.
- Track who generated/exported data.
- Track submission status and acknowledgement.
- Preserve old submissions.
- Do not direct-submit to government systems unless official API access and authorization are confirmed.

## Security Review Checklist

Before completing sensitive changes:

- Are all endpoints authorized?
- Are object-level checks present?
- Is tenant/school/session scoping enforced?
- Are sensitive fields masked?
- Are exports logged?
- Are audit events emitted?
- Are inputs validated?
- Are dangerous state transitions protected?
- Are uploads safe?
- Are payment webhooks verified and idempotent?
- Are tests covering permission failures?

## Official References

- OWASP API Security Top 10: <https://owasp.org/API-Security/>
- OWASP ASVS: <https://owasp.org/www-project-application-security-verification-standard/>
- OWASP Cheat Sheet Series: <https://cheatsheetseries.owasp.org/>
- India Code: <https://www.indiacode.nic.in/>
- MeitY: <https://www.meity.gov.in/>

