package in.chalkbase.school.api;

import in.chalkbase.platform.classification.Classification;
import in.chalkbase.platform.classification.Classified;
import in.chalkbase.platform.classification.Tier;

/**
 * The minimum another module needs to know about a school: how it is addressed, what to call it,
 * and which PostgreSQL schema its data lives in.
 *
 * <p>Deliberately not {@link SchoolResponse} — that is a read model for HTTP clients and will grow
 * fields. This is the cross-module contract and stays small.
 *
 * @param code the code a user types, e.g. on the login form
 * @param name the school's display name
 * @param schemaName the tenant schema to bind before touching that school's data (ADR-0011)
 */
public record SchoolRef(
        @Classification(Tier.PUBLIC) String code,
        @Classification(Tier.PUBLIC) String name,
        @Classification(Tier.INTERNAL) String schemaName) {
    /** Redacted by tier: ADR-0014 forbids Confidential and Restricted values in any log sink. */
    @Override
    public String toString() {
        return Classified.describe(this);
    }
}
