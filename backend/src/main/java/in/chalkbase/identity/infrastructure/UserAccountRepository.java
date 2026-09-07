package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.application.AccountStanding;
import in.chalkbase.identity.domain.AccountStatus;
import in.chalkbase.identity.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every query here is implicitly scoped to one school: the connection's {@code search_path} decides
 * which schema {@code user_account} lives in, so there is no tenant filter to write or to forget
 * (ADR-0011). A method taking a school id would be a review blocker.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /**
     * {@code status}, {@code lockedUntil} and {@code mustChangePassword}, without loading the
     * account (ADR-0023).
     *
     * <p>This runs on every API call a signed-in session makes ({@code SessionStandingFilter}), so
     * it projects three columns by primary key rather than hydrating an entity that would then be
     * dirty-checked at flush for a read nobody writes back — the same reasoning that kept the
     * single-column {@code findMustChangePassword} this replaces cheap.
     *
     * @return empty when there is no such account — a session whose account has since been deleted
     */
    @Query("select new in.chalkbase.identity.application.AccountStanding(a.status, a.lockedUntil, a.mustChangePassword)"
            + " from UserAccount a where a.id = :accountId")
    Optional<AccountStanding> findStanding(UUID accountId);

    /** The subset of {@code accountIds} currently {@link AccountStatus#ACTIVE}. For the last-access-manager guard. */
    @Query("select a.id from UserAccount a where a.id in :accountIds and a.status = :status")
    List<UUID> findIdsByIdInAndStatus(
            @Param("accountIds") Iterable<UUID> accountIds, @Param("status") AccountStatus status);
}
