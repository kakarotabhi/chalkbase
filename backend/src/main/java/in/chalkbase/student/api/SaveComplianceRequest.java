package in.chalkbase.student.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import jakarta.validation.constraints.Size;

/**
 * A student's UDISE+/board identifiers and statutory categories, entered or corrected (FR-029).
 *
 * <p><strong>{@code apaarId} may not be set unless {@code apaarConsentGiven} is true.</strong> APAAR
 * is consent-based (the compliance requirements; ADR-0014's consent section names it specifically),
 * and a field that is only lawful to hold with consent needs somewhere to record that consent was
 * given — the alternative is an id sitting in the database with no way to show it was ever lawful to
 * collect. {@code StudentRecordService} enforces this and refuses the write otherwise
 * ({@code StudentErrorCode#APAAR_REQUIRES_CONSENT}); it is not a {@code @AssertTrue} here because
 * the message needs to name which of the two fields is missing, which a class-level constraint on a
 * record cannot do as clearly as a service-level check with its own error code.
 *
 * <p>{@code apaarConsentGivenAt} is not a request field: it is set by the server, from the moment
 * consent first becomes true, the same way {@code created_at} is server time rather than
 * client-supplied.
 *
 * @param apaarConsentGivenBy who gave the consent — a guardian's name, typed by the office from the
 *     signed form. Not a reference to a {@code guardian} row: the person who signs an APAAR consent
 *     form is not always the guardian already on record as primary, and this asks for the name that
 *     was actually on the form rather than forcing a match to one that might not be.
 */
public record SaveComplianceRequest(
        @Classification(Tier.CONFIDENTIAL) @Size(max = 40) String penUdiseId,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 40) String boardRegistrationNumber,
        @Classification(Tier.RESTRICTED) @Size(max = 100) String casteCategory,
        @Classification(Tier.RESTRICTED) @Size(max = 100) String religion,
        @Classification(Tier.RESTRICTED) @Size(max = 100) String specialCategory,
        @Classification(Tier.RESTRICTED) @Size(max = 40) String apaarId,
        @Classification(Tier.INTERNAL) boolean apaarConsentGiven,
        @Classification(Tier.CONFIDENTIAL) @Size(max = 200) String apaarConsentGivenBy) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
