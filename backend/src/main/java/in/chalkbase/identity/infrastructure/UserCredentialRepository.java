package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.domain.CredentialStatus;
import in.chalkbase.identity.domain.CredentialType;
import in.chalkbase.identity.domain.UserCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    Optional<UserCredential> findByAccount_IdAndTypeAndStatus(
            UUID accountId, CredentialType type, CredentialStatus status);
}
