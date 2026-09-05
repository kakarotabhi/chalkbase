package in.chalkbase.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A client compares this value with the one it is holding to decide whether its view is stale
 * (ADR-0008), so the only two properties that matter are that the same set always produces the same
 * value and that a different set never does.
 */
class PermissionsVersionTests {

    private static final String SCHOOL_READ = "school:school:read";
    private static final String USER_READ = "identity:user:read";
    private static final String ROLE_MANAGE = "identity:role:manage";

    @Test
    void isTheSameForTheSameSetHoweverItArrived() {
        String sorted = PermissionsVersion.of(List.of(ROLE_MANAGE, SCHOOL_READ, USER_READ));

        assertThat(PermissionsVersion.of(List.of(SCHOOL_READ, USER_READ, ROLE_MANAGE)))
                .isEqualTo(sorted);
        assertThat(PermissionsVersion.of(new LinkedHashSet<>(List.of(USER_READ, ROLE_MANAGE, SCHOOL_READ))))
                .isEqualTo(sorted);
        // The union of two grants can produce the same code twice; that is not a different set.
        assertThat(PermissionsVersion.of(List.of(SCHOOL_READ, SCHOOL_READ, USER_READ, ROLE_MANAGE)))
                .isEqualTo(sorted);
    }

    @Test
    void changesWhenTheSetChangesByOneCode() {
        String both = PermissionsVersion.of(List.of(SCHOOL_READ, USER_READ));

        assertThat(PermissionsVersion.of(List.of(SCHOOL_READ))).isNotEqualTo(both);
        assertThat(PermissionsVersion.of(List.of(SCHOOL_READ, USER_READ, ROLE_MANAGE)))
                .isNotEqualTo(both);
    }

    /**
     * Holding nothing is a permission set like any other, and gets a version. A null would make
     * every client special-case the parent who has not been granted anything yet.
     */
    @Test
    void holdingNothingStillHasAVersion() {
        assertThat(PermissionsVersion.of(Set.of()))
                .isNotBlank()
                .isEqualTo(PermissionsVersion.of(List.of()))
                .isNotEqualTo(PermissionsVersion.of(List.of(SCHOOL_READ)));
    }

    /** No two different sets may canonicalise to the same string before hashing. */
    @Test
    void doesNotConfuseOneLongCodeWithTwoShortOnes() {
        assertThat(PermissionsVersion.of(List.of("a:b:c", "d:e:f")))
                .isNotEqualTo(PermissionsVersion.of(List.of("a:b:cd:e:f")));
    }
}
