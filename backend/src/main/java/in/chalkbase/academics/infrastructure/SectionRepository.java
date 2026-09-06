package in.chalkbase.academics.infrastructure;

import in.chalkbase.academics.domain.Section;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Sections, reached by id for editing. Reading them for a screen is done through
 * {@code SchoolClassRepository}, because a section is only ever shown inside its class.
 *
 * <p>Scoped by the connection's {@code search_path} (ADR-0011): a section id from another school
 * simply is not in this schema, so an edit aimed at one is a 404 rather than a leak.
 */
public interface SectionRepository extends JpaRepository<Section, UUID> {

    /**
     * One section with the class it divides, in one query.
     *
     * <p>For {@code AcademicsLookupService}, which has to answer "Class 5 A" and not just "A".
     * {@code findById} would leave the class proxy to be initialised afterwards, which works and
     * costs a second round trip per call.
     */
    @Query("select s from Section s join fetch s.schoolClass where s.id = :id")
    Optional<Section> findWithClassById(UUID id);

    /**
     * Several sections with their classes, in one query.
     *
     * <p>The batch half of the same thing: a page of thirty students in eight sections is one
     * statement rather than eight, and the join is what stops it being sixteen.
     */
    @Query("select s from Section s join fetch s.schoolClass where s.id in :ids")
    List<Section> findAllWithClassByIdIn(Collection<UUID> ids);

    /**
     * Every section of this school with its class, in one query.
     *
     * <p>For a caller resolving <em>names</em> rather than ids — the bulk import, which holds "Class
     * 5" and "A" and has to turn six hundred of those into section ids. Whole rather than paged
     * because a school's ladder is tens of rows, and reading it once is what stops the import
     * issuing one lookup per student.
     */
    @Query("select s from Section s join fetch s.schoolClass order by s.schoolClass.sequence, s.name")
    List<Section> findAllWithClass();
}
