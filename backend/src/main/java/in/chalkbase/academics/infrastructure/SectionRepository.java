package in.chalkbase.academics.infrastructure;

import in.chalkbase.academics.domain.Section;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Sections, reached by id for editing. Reading them is done through {@code SchoolClassRepository},
 * because a section is only ever shown inside its class.
 *
 * <p>Scoped by the connection's {@code search_path} (ADR-0011): a section id from another school
 * simply is not in this schema, so an edit aimed at one is a 404 rather than a leak.
 */
public interface SectionRepository extends JpaRepository<Section, UUID> {}
