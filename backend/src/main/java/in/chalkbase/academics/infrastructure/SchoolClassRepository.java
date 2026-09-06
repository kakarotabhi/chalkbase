package in.chalkbase.academics.infrastructure;

import in.chalkbase.academics.domain.SchoolClass;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * This school's ladder of classes. Scoped by the connection's {@code search_path}, so there is no
 * tenant filter to write or to forget (ADR-0011).
 */
public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {

    /**
     * The whole ladder with its sections, in one query.
     *
     * <p>Inactive rows are returned too, flagged. The client decides how to show a class that has
     * been retired — hiding it here would make a deactivated class invisible to the only screen
     * that can bring it back.
     *
     * <p>A fetch join rather than a lazy collection walked per class: fourteen classes would
     * otherwise be fifteen queries. Hibernate de-duplicates the root entities.
     */
    @Query("select c from SchoolClass c left join fetch c.sections order by c.sequence")
    List<SchoolClass> findAllWithSections();

    /**
     * The last rung, so a new class can be appended after it.
     *
     * <p>Empty for a school that has no classes yet — no ladder is seeded, because schools
     * genuinely disagree about where theirs starts and ends (ADR-0019).
     */
    @Query("select max(c.sequence) from SchoolClass c")
    Optional<Integer> highestSequence();
}
