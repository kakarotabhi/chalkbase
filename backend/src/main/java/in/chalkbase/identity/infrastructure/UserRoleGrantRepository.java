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

    /**
     * Every grant, for every user in the school, that is in force on {@code on} and whose role
     * carries {@code permission} — for {@code AccessGuardrails}, which asks "who could still manage
     * access after this write?" rather than "what can one user do?", so it is not scoped to a user
     * the way {@link #findInForce} is.
     */
    @Query("""
            select distinct g from UserRoleGrant g
                join fetch g.role r
            where :permission member of r.permissions
              and (g.validFrom is null or g.validFrom <= :on)
              and (g.validTo is null or g.validTo >= :on)
            """)
    List<UserRoleGrant> findInForceGranting(@Param("permission") String permission, @Param("on") LocalDate on);

    /** Every grant of one role, regardless of validity window — for simulating a role losing a permission. */
    List<UserRoleGrant> findByRole_Id(UUID roleId);

    /**
     * Every grant one account holds, regardless of validity window, with its role loaded — an admin
     * managing a user's access wants to see (and revoke) an expired or not-yet-started grant too, not
     * just the ones {@link #findInForce} would count for authorization.
     */
    @Query("select g from UserRoleGrant g join fetch g.role where g.userAccountId = :userAccountId")
    List<UserRoleGrant> findByUserAccountId(@Param("userAccountId") UUID userAccountId);
}
