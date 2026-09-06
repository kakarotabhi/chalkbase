/**
 * Student: the child's record, the people responsible for them, and where they sit this year.
 *
 * <p>Owns {@code student}, {@code guardian}, {@code student_guardian} and
 * {@code student_enrolment} (ADR-0020). It is the largest concentration of children's personal data
 * in the product, and every field on every one of those tables is <strong>Confidential</strong>
 * under ADR-0014 — names, dates of birth, phone numbers, an admission number that identifies one
 * child. None of it may be logged at any level, appear in an error message, or reach a third party.
 * The audit log records field names only, which is what {@code AuditService} already enforces.
 *
 * <p>Three shapes here are decisions rather than conveniences, and each is argued in ADR-0020:
 *
 * <ul>
 *   <li><strong>One name field.</strong> Not first/middle/last. A great many Indian students have no
 *       surname; a required "last name" box makes the office invent one, and what they invent goes
 *       on the certificate.
 *   <li><strong>Enrolment is its own record and it carries the session.</strong> Classes and
 *       sections are structural (ADR-0019), so the year lives here — which makes promotion a new
 *       row rather than an edit, and a student's history readable without the audit log.
 *   <li><strong>Guardians are shared.</strong> Siblings link to one person, so correcting a father's
 *       phone number once corrects it for all four of his children. Copying a guardian per child
 *       leaves three of them with a number that no longer answers and nothing that knows they
 *       disagree.
 * </ul>
 *
 * <p><strong>Nothing here deletes a person.</strong> A student is {@code WITHDRAWN} or
 * {@code TRANSFERRED}; a guardian record survives being unlinked. Fees, attendance and marks all
 * reference a student, and these are the records a school is legally required to produce years
 * later. Erasure under the DPDP Act is a different operation with its own design, and a
 * {@code DELETE} endpoint would never have been an answer to it.
 *
 * <p>What is <strong>deliberately absent</strong> is a recorded blocker, not a scope preference:
 * caste and community, religion, disability and CWSN status, EWS/BPL/RTE category, guardian income,
 * APAAR and Aadhaar are Restricted under ADR-0014, which requires encryption at rest, masking and
 * read-auditing. None of that machinery exists, and UDISE+ returns need those fields — so
 * encryption at rest is on the critical path before the first real school onboards.
 *
 * <p>This is the first module to depend on another feature module: it reaches {@code academics}
 * through {@code academics.api.AcademicsLookup} for the session, class and section an enrolment
 * names, and through nothing else.
 *
 * <p>Every table this module owns is per-tenant and carries no {@code school_id}: the PostgreSQL
 * schema is the tenant boundary (ADR-0011).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Student")
package in.chalkbase.student;
