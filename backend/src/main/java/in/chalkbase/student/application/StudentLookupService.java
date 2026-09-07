package in.chalkbase.student.application;

import in.chalkbase.student.api.EnrolledStudentRef;
import in.chalkbase.student.api.SectionEnrolmentCount;
import in.chalkbase.student.api.StudentLookup;
import in.chalkbase.student.api.StudentNameRef;
import in.chalkbase.student.infrastructure.GuardianRepository;
import in.chalkbase.student.infrastructure.StudentEnrolmentRepository;
import in.chalkbase.student.infrastructure.StudentRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final StudentRepository students;

    public StudentLookupService(
            StudentEnrolmentRepository enrolments, GuardianRepository guardians, StudentRepository students) {
        this.enrolments = enrolments;
        this.guardians = guardians;
        this.students = students;
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

    @Override
    public List<EnrolledStudentRef> rosterOfSection(UUID sectionId, UUID academicSessionId) {
        if (sectionId == null || academicSessionId == null) {
            return List.of();
        }
        return enrolments.findRosterOfSection(academicSessionId, sectionId).stream()
                .map(enrolment -> new EnrolledStudentRef(
                        enrolment.getStudent().getId(),
                        enrolment.getStudent().getAdmissionNumber(),
                        enrolment.getStudent().getFullName(),
                        enrolment.getRollNumber()))
                .toList();
    }

    @Override
    public Map<UUID, StudentNameRef> namesOf(Collection<UUID> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        Collection<UUID> distinct = studentIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<UUID, StudentNameRef> byId = new LinkedHashMap<>();
        for (var student : students.findAllById(distinct)) {
            byId.put(
                    student.getId(),
                    new StudentNameRef(student.getId(), student.getAdmissionNumber(), student.getFullName()));
        }
        return byId;
    }
}
