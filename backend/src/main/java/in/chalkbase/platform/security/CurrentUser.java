package in.chalkbase.platform.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Who is acting, as a plain {@code UUID} a module may store on a row it owns.
 *
 * <p>Collects every {@link CurrentUserResolver} the same way {@code AuditService} collects
 * {@code AuditActorResolver} — asks each in turn, takes the first answer — so a module never needs
 * to know which one applies, or that {@code identity} is the module answering it today.
 */
@Service
public class CurrentUser {

    private final List<CurrentUserResolver> resolvers;

    public CurrentUser(List<CurrentUserResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    /** Empty when nobody is authenticated — a scheduled job, or a request that never should reach here. */
    public Optional<UUID> id() {
        return resolvers.stream()
                .flatMap(resolver -> resolver.currentUserId().stream())
                .findFirst();
    }

    /**
     * As {@link #id()}, for a call site that has already established a session must exist —
     * every attendance-writing endpoint requires authentication, so an empty result here means the
     * security filter chain and this class have drifted apart, not that a caller forgot to check.
     *
     * @throws IllegalStateException if nobody is authenticated
     */
    public UUID require() {
        return id().orElseThrow(() -> new IllegalStateException(
                "CurrentUser.require() with nobody authenticated. This call site assumes a session already exists;"
                        + " check the endpoint's own authorization instead of relaxing this one."));
    }
}
