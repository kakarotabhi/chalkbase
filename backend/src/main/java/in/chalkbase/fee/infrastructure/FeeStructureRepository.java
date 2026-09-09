package in.chalkbase.fee.infrastructure;

import in.chalkbase.fee.domain.FeeStructure;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, UUID> {

    /** Every class's live version for one session — the read a structure list screen makes. */
    List<FeeStructure> findByAcademicSessionIdAndSupersededAtIsNull(UUID academicSessionId);

    /** One class's live version for one session, if it has one. */
    Optional<FeeStructure> findByAcademicSessionIdAndSchoolClassIdAndSupersededAtIsNull(
            UUID academicSessionId, UUID schoolClassId);

    /** The highest version number this (session, class) has ever had, so the next one can be chosen. */
    Optional<FeeStructure> findFirstByAcademicSessionIdAndSchoolClassIdOrderByVersionDesc(
            UUID academicSessionId, UUID schoolClassId);

    /** Whether this (session, class) has ever had a structure at all — the backfill-allowed check. */
    boolean existsByAcademicSessionIdAndSchoolClassId(UUID academicSessionId, UUID schoolClassId);
}
