package in.chalkbase.student.application;

import in.chalkbase.student.api.SectionEnrolmentCount;
import in.chalkbase.student.api.StudentLookup;
import in.chalkbase.student.infrastructure.GuardianRepository;
import in.chalkbase.student.infrastructure.StudentEnrolmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What {@link StudentLookup} promises, answered from this module's own repositories.
 *
 * <p>Mirrors {@code academics.application.AcademicsLookupService}: read-only throughout, no
 * {@code @Transactional} write anywhere, and kept apart from {@link StudentService} and
 * {@link GuardianService} — those two are the read and write models behind a screen and will grow
 * with it, and folding this in would let a change made for a screen quietly change what another
 * module sees.
 *
 * <p>Tenant-scoped like everything else: no school argument, because the schema is the boundary
 * (ADR-0011).
 */
@Service
@Transactional(readOnly = true)
public class StudentLookupService implements StudentLookup {

    private final StudentEnrolmentRepository enrolments;
    private final GuardianRepository guardians;

    public StudentLookupService(StudentEnrolmentRepository enrolments, GuardianRepository guardians) {
        this.enrolments = enrolments;
        this.guardians = guardians;
    }

    @Override
    public long activeEnrolmentCount(UUID academicSessionId) {
        return academicSessionId == null ? 0 : enrolments.countByActiveTrueAndAcademicSessionId(academicSessionId);
    }

    @Override
    public List<SectionEnrolmentCount> activeEnrolmentCountsBySection(UUID academicSessionId) {
        if (academicSessionId == null) {
            return List.of();
        }
        return enrolments.countActiveGroupedBySection(academicSessionId).stream()
                .map(row -> new SectionEnrolmentCount((UUID) row[0], (Long) row[1]))
                .toList();
    }

    @Override
    public long activeStudentsWithoutAGuardianCount(UUID academicSessionId) {
        return academicSessionId == null ? 0 : enrolments.countActiveWithoutGuardian(academicSessionId);
    }

    @Override
    public long guardiansWithoutAStudentCount() {
        return guardians.countWithoutAnyStudent();
    }
}
