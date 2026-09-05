package in.chalkbase.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * One of this school's roles, with the permissions it currently holds.
 *
 * @param templateCode the shipped template this role was copied from at onboarding, or null if the
 *     school created it. Provenance only: the role is the school's, and editing it changes nothing
 *     at any other school (ADR-0005).
 */
public record RoleResponse(
        UUID id, String code, String name, String description, String templateCode, List<String> permissions) {}
