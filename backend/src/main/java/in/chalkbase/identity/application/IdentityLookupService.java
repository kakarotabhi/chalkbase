package in.chalkbase.identity.application;

import in.chalkbase.identity.api.IdentityLookup;
import in.chalkbase.identity.api.UserSummary;
import in.chalkbase.identity.domain.AccountStatus;
import in.chalkbase.identity.domain.UserAccount;
import in.chalkbase.identity.infrastructure.UserAccountRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What {@link IdentityLookup} promises, answered from this module's own repository.
 *
 * <p>Read-only throughout, mirroring {@code AcademicsLookupService} and {@code StudentLookupService}:
 * the interface exists so another module can resolve an account it points at, never so it can change
 * one. Separate from {@link AccessDirectory}, which is the access screens' own read model and will
 * grow with them — this class answers a different question, "what is this id", for callers outside
 * the module.
 */
@Service
@Transactional(readOnly = true)
public class IdentityLookupService implements IdentityLookup {

    private final UserAccountRepository accounts;

    public IdentityLookupService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public List<UserSummary> activeUsers() {
        return accounts.findByStatusOrderByDisplayNameAsc(AccountStatus.ACTIVE).stream()
                .map(IdentityLookupService::toSummary)
                .toList();
    }

    @Override
    public Map<UUID, UserSummary> usersOf(Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        Collection<UUID> distinct =
                accountIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return accounts.findAllById(distinct).stream()
                .map(IdentityLookupService::toSummary)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    private static UserSummary toSummary(UserAccount account) {
        return new UserSummary(
                account.getId(), account.getDisplayName(), account.getStatus().name());
    }
}
