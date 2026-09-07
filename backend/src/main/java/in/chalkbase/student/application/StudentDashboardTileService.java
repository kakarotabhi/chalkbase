package in.chalkbase.student.application;

import in.chalkbase.academics.api.AcademicSessionRef;
import in.chalkbase.academics.api.AcademicsLookup;
import in.chalkbase.academics.api.SchoolClassRef;
import in.chalkbase.academics.api.SectionRef;
import in.chalkbase.platform.dashboard.ClassEnrolmentCount;
import in.chalkbase.platform.dashboard.LinkageGapsTile;
import in.chalkbase.platform.dashboard.StudentDashboardContributor;
import in.chalkbase.platform.dashboard.StudentsTile;
import in.chalkbase.student.api.SectionEnrolmentCount;
import in.chalkbase.student.api.StudentLookup;
import in.chalkbase.student.infrastructure.StudentPermissions;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers {@link StudentDashboardContributor} from {@link StudentLookup} and
 * {@link AcademicsLookup} — the same two interfaces any other caller outside this module would use,
 * so this tile adds no second way to read enrolment or guardian data.
 *
 * <p>Depending on {@code academics.api} here is not new: {@code student}'s own package-info already
 * says it is "the first module to depend on another feature module", reaching {@code academics}
 * through {@link AcademicsLookup} for the session, class and section an enrolment names. This class
 * does the same thing for the same reason — resolving which class a section belongs to, so the
 * tile can report enrolment by class rather than by an opaque section id.
 *
 * <p>Kept apart from {@link StudentLookupService} and the write-side services for the same reason
 * {@code AcademicsDashboardTileService} is kept apart from {@code AcademicsLookupService}: a
 * dashboard tile is screen-shaped, and folding it into the lookup service would let a change made
 * for this one screen quietly change what every other caller of {@link StudentLookup} sees.
 */
@Service
@Transactional(readOnly = true)
class StudentDashboardTileService implements StudentDashboardContributor {

    private final AcademicsLookup academics;
    private final StudentLookup students;

    StudentDashboardTileService(AcademicsLookup academics, StudentLookup students) {
        this.academics = academics;
        this.students = students;
    }

    @Override
    public Optional<StudentsTile> studentsTile(Set<String> heldPermissions) {
        if (!heldPermissions.contains(StudentPermissions.STUDENT_READ)) {
            return Optional.empty();
        }
        return academics.currentSession().map(session -> {
            long enrolled = students.activeEnrolmentCount(session.id());
            List<ClassEnrolmentCount> byClass = countsByClass(students.activeEnrolmentCountsBySection(session.id()));
            return new StudentsTile(enrolled, byClass);
        });
    }

    @Override
    public Optional<LinkageGapsTile> linkageGapsTile(Set<String> heldPermissions) {
        boolean canSeeStudents = heldPermissions.contains(StudentPermissions.STUDENT_READ);
        boolean canSeeGuardians = heldPermissions.contains(StudentPermissions.GUARDIAN_READ);
        if (!canSeeStudents && !canSeeGuardians) {
            return Optional.empty();
        }

        Long studentsWithoutAGuardian = canSeeStudents ? studentsWithoutAGuardian() : null;
        Long guardiansWithoutAStudent = canSeeGuardians ? students.guardiansWithoutAStudentCount() : null;
        return Optional.of(new LinkageGapsTile(studentsWithoutAGuardian, guardiansWithoutAStudent));
    }

    private Long studentsWithoutAGuardian() {
        return academics
                .currentSession()
                .map(AcademicSessionRef::id)
                .map(students::activeStudentsWithoutAGuardianCount)
                .orElse(null);
    }

    /**
     * Resolves each section's class through {@link AcademicsLookup} and sums — this module only
     * knows a section id ({@code StudentEnrolment.getSectionId()}, ADR-0020 §4), never its class,
     * so the join happens here rather than in either module's database.
     */
    private List<ClassEnrolmentCount> countsByClass(List<SectionEnrolmentCount> bySection) {
        if (bySection.isEmpty()) {
            return List.of();
        }
        Map<UUID, SectionRef> sections = academics.sections(
                bySection.stream().map(SectionEnrolmentCount::sectionId).toList());
        Map<UUID, SchoolClassRef> classesById =
                academics.classes().stream().collect(Collectors.toMap(SchoolClassRef::id, Function.identity()));

        Map<UUID, Long> totalsByClass = new LinkedHashMap<>();
        Map<UUID, String> classNames = new LinkedHashMap<>();
        for (SectionEnrolmentCount row : bySection) {
            SectionRef section = sections.get(row.sectionId());
            if (section == null) {
                // A section id enrolment points at but the ladder no longer resolves — retired and
                // since removed, or a race with another request. Skipped rather than guessed: a
                // count against an unnamed class is not a tile anyone can act on.
                continue;
            }
            totalsByClass.merge(section.classId(), row.count(), Long::sum);
            classNames.putIfAbsent(section.classId(), section.className());
        }

        return totalsByClass.entrySet().stream()
                .map(entry -> {
                    UUID classId = entry.getKey();
                    SchoolClassRef schoolClass = classesById.get(classId);
                    int sequence = schoolClass != null ? schoolClass.sequence() : Integer.MAX_VALUE;
                    return new ClassEnrolmentCount(classId, classNames.get(classId), sequence, entry.getValue());
                })
                .sorted(Comparator.comparingInt(ClassEnrolmentCount::sequence))
                .toList();
    }
}
