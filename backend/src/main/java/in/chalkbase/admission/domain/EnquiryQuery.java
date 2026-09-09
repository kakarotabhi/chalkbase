package in.chalkbase.admission.domain;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;
import java.util.UUID;

/**
 * What to narrow the enquiry list by. Every field is optional; all of them are ANDed.
 *
 * @param q free text over the child's name, the parent's name and the parent's phone number — the
 *     three things a front office has written down in front of them, the same reasoning
 *     {@code StudentQuery.q} gives for a student's name and admission number.
 * @param status one of the four. Absent means every status, closed ones included — a school
 *     reviewing last month's lost enquiries needs them in this list, not filtered out by default.
 *     {@code Tier.INTERNAL}, the same tier {@code StudentQuery.status} gives the closest analogue
 *     in the codebase: an administrative status word, not the child's or family's own data.
 * @param source one of the six FR-016 names.
 * @param assignedCounsellorId enquiries assigned to one counsellor — what a manager uses to see one
 *     person's workload, and what the counsellor's own list defaults to.
 * @param interestedClassId enquiries naming one class as the child's intended class.
 */
public record EnquiryQuery(
        @Classification(Tier.CONFIDENTIAL) String q,
        @Classification(Tier.INTERNAL) EnquiryStatus status,
        @Classification(Tier.INTERNAL) EnquirySource source,
        @Classification(Tier.INTERNAL) UUID assignedCounsellorId,
        @Classification(Tier.INTERNAL) UUID interestedClassId) {

    /** Everything, unnarrowed. */
    public static EnquiryQuery all() {
        return new EnquiryQuery(null, null, null, null, null);
    }

    public boolean hasText() {
        return q != null && !q.isBlank();
    }

    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
