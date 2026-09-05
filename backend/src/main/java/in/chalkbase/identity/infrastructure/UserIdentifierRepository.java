package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.domain.IdentifierType;
import in.chalkbase.identity.domain.UserIdentifier;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserIdentifierRepository extends JpaRepository<UserIdentifier, UUID> {

    /**
     * The account is fetched eagerly on purpose: the caller reads it after the transaction that
     * loaded it has closed, and a lazy proxy would fail there.
     */
    @Query("select i from UserIdentifier i join fetch i.account where i.type = :type and i.value = :value")
    Optional<UserIdentifier> findWithAccount(@Param("type") IdentifierType type, @Param("value") String value);
}
