package in.chalkbase.identity.infrastructure;

import in.chalkbase.identity.domain.UserRoleGrant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleGrantRepository extends JpaRepository<UserRoleGrant, UUID> {

    /**
     * Every grant this user holds that is in force on {@code on}, with its role and that role's
     * permissions already loaded.
     *
     * <p>The validity window is applied in the query rather than over the results: a grant outside
     * its window contributes nothing, so there is no reason to load it. The fetch joins are what
     * keep this to one statement — a handful of grants each triggering two more selects is the
     * shape this would otherwise take, on the one query that runs for every login.
     *
     * <p>Both bounds are inclusive: a grant valid to the 31st is still in force on the 31st.
     */
    @Query("""
            select distinct g from UserRoleGrant g
                join fetch g.role r
                left join fetch r.permissions
            where g.userAccountId = :userAccountId
              and (g.validFrom is null or g.validFrom <= :on)
              and (g.validTo is null or g.validTo >= :on)
            """)
    List<UserRoleGrant> findInForce(@Param("userAccountId") UUID userAccountId, @Param("on") LocalDate on);
}
