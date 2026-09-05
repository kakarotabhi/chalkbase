package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.domain.UserAccount;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query here is implicitly scoped to one school: the connection's {@code search_path} decides
 * which schema {@code user_account} lives in, so there is no tenant filter to write or to forget
 * (ADR-0011). A method taking a school id would be a review blocker.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {}
