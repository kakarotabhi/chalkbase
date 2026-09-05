package in.chalkbase.identity.api;

import java.util.UUID;

/**
 * Enough of an account to list it. No identifier, no credential, no login history — everything here
 * is already on screen wherever this list is shown.
 */
public record UserSummary(UUID id, String displayName, String status) {}
