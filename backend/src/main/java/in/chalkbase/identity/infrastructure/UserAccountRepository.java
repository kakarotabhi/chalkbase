package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Every query here is implicitly scoped to one school: the connection's {@code search_path} decides
 * which schema {@code user_account} lives in, so there is no tenant filter to write or to forget
 * (ADR-0011). A method taking a school id would be a review blocker.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /**
     * Just the forced-change flag, without loading the account.
     *
     * <p>This runs on every API call a signed-in session makes ({@code PasswordChangeRequiredFilter}),
     * so it selects one boolean column by primary key rather than hydrating an entity that would
     * then be dirty-checked at flush for a read nobody writes back.
     *
     * @return empty when there is no such account — a session whose account has since been deleted
     */
    @Query("select a.mustChangePassword from UserAccount a where a.id = :accountId")
    Optional<Boolean> findMustChangePassword(UUID accountId);
}
