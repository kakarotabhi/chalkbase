package in.chalkbase.student.application;

import in.chalkbase.platform.api.PageResponse;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import in.chalkbase.student.api.GuardianSummary;
import in.chalkbase.student.api.LinkGuardianRequest;
import in.chalkbase.student.api.SaveGuardianRequest;
import in.chalkbase.student.api.StudentGuardian;
import in.chalkbase.student.api.UpdateStudentGuardianRequest;
import in.chalkbase.student.domain.Guardian;
import in.chalkbase.student.domain.Student;
import in.chalkbase.student.domain.StudentAudit;
import in.chalkbase.student.domain.StudentErrorCode;
import in.chalkbase.student.domain.StudentGuardianLink;
import in.chalkbase.student.infrastructure.GuardianRepository;
import in.chalkbase.student.infrastructure.StudentGuardianRepository;
import in.chalkbase.student.infrastructure.StudentRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The people responsible for this school's children, and which of them is responsible for whom.
 *
 * <p><strong>A guardian is a person record, not an account</strong> (ADR-0017 §4), and one person is
 * shared between siblings rather than copied per child (ADR-0020 §5). Both of those show up here as
 * the same thing: the directory exists so the office can attach an existing father to his second
 * child instead of typing him in again, and editing him reaches all four of his children in one
 * write.
 *
 * <p>Scoped to the school bound to this request (ADR-0011). Every field handled here — a name, a
 * phone number, an email address — is Confidential under ADR-0014 and appears in no log line and no
 * error message.
 *
 * <p>Audited per ADR-0018: field names only, in the caller's transaction. A change to a guardian is
 * recorded against the guardian's own id, because it is one change to one shared person; a change to
 * a <em>link</em> is recorded against the student's, because "what happened to this child" is the
 * question the log is asked. {@code StudentAudit} says why at length.
 */
@Service
@Transactional(readOnly = true)
public class GuardianService {

    private final GuardianRepository guardians;
    private final StudentGuardianRepository links;
    private final StudentRepository students;
    private final AuditService audit;

    public GuardianService(
            GuardianRepository guardians,
            StudentGuardianRepository links,
            StudentRepository students,
            AuditService audit) {
        this.guardians = guardians;
        this.links = links;
        this.students = students;
        this.audit = audit;
    }

    // ── The directory ────────────────────────────────────────────────────────────────────────

    /**
     * One page of guardians, with how many children each is linked to.
     *
     * <p>Two queries whatever the page size: the people, then one grouped count for the ids on the
     * page. A count per row would be twenty-six queries for a page of twenty-five, on the screen a
     * clerk uses most.
     *
     * <p>The free text covers name, phone and email, because all three are things somebody has in
     * front of them — a number on a form, an address on a letter. It does not cover the children,
     * for the reason {@code GuardianSummary} gives: this screen finds one person, it does not
     * enumerate families.
     */
    public PageResponse<GuardianSummary> list(String q, Pageable pageable) {
        Page<Guardian> page = guardians.findAll(matching(q), pageable);
        Map<UUID, Long> counts = linkedStudentCounts(
                page.getContent().stream().map(Guardian::getId).toList());

        List<GuardianSummary> content = page.getContent().stream()
                .map(guardian -> GuardianSummary.of(guardian, counts.getOrDefault(guardian.getId(), 0L)))
                .toList();
        return PageResponse.of(page, content);
    }

    /**
     * A new person.
     *
     * <p>Not attached to anybody yet, and that is the honest shape: creating a guardian and deciding
     * whose parent they are are two acts, and folding them together is how the same father ends up
     * in the table once per child.
     */
    @Transactional
    public GuardianSummary create(SaveGuardianRequest request) {
        Guardian guardian = guardians.saveAndFlush(new Guardian(
                request.fullName().trim(),
                trimmedOrNull(request.phone()),
                trimmedOrNull(request.email()),
                trimmedOrNull(request.occupation())));

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                StudentAudit.GUARDIAN,
                guardian.getId().toString(),
                List.of("fullName", "phone", "email", "occupation"));

        return GuardianSummary.of(guardian, 0L);
    }

    /**
     * Corrects a person's details, for every child they are linked to at once.
     *
     * <p>That reach is the feature, not a side effect (ADR-0020 §5): a father's new phone number is
     * new for all four of his children, and a model that made the school correct it four times is a
     * model where three of those corrections do not happen.
     */
    @Transactional
    public GuardianSummary update(UUID id, SaveGuardianRequest request) {
        Guardian guardian = requireGuardian(id);
        String fullName = request.fullName().trim();
        String phone = trimmedOrNull(request.phone());
        String email = trimmedOrNull(request.email());
        String occupation = trimmedOrNull(request.occupation());

        Set<String> changed = new LinkedHashSet<>();
        if (!Objects.equals(guardian.getFullName(), fullName)) {
            changed.add("fullName");
        }
        if (!Objects.equals(guardian.getPhone(), phone)) {
            changed.add("phone");
        }
        if (!Objects.equals(guardian.getEmail(), email)) {
            changed.add("email");
        }
        if (!Objects.equals(guardian.getOccupation(), occupation)) {
            changed.add("occupation");
        }
        long linked = linkedStudentCounts(List.of(id)).getOrDefault(id, 0L);
        if (changed.isEmpty()) {
            return GuardianSummary.of(guardian, linked);
        }

        guardian.apply(fullName, phone, email, occupation);
        guardians.saveAndFlush(guardian);

        audit.recordChange(
                AuditAction.ENTITY_UPDATED,
                StudentAudit.GUARDIAN,
                guardian.getId().toString(),
                changed);

        return GuardianSummary.of(guardian, linked);
    }

    // ── Links ────────────────────────────────────────────────────────────────────────────────

    /**
     * Attaches an existing guardian to a child.
     *
     * <p>Takes a guardian id, never a name — {@code LinkGuardianRequest} argues why. The same person
     * twice on one child is refused by {@code uq_student_guardian_pair}, which is almost always an
     * operator adding "father" a second time rather than editing the first.
     */
    @Transactional
    public StudentGuardian link(UUID studentId, LinkGuardianRequest request) {
        Student student = requireStudent(studentId);
        Guardian guardian = guardians
                .findById(request.guardianId())
                .orElseThrow(() -> new ChalkbaseException(StudentErrorCode.UNKNOWN_GUARDIAN));

        if (request.primary()) {
            clearExistingPrimary(studentId, null);
        }

        StudentGuardianLink link =
                links.saveAndFlush(new StudentGuardianLink(student, guardian, request.relation(), request.primary()));

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                StudentAudit.STUDENT_GUARDIAN,
                student.getId().toString(),
                List.of("guardianId", "relation", "primary"));

        return StudentGuardian.of(link);
    }

    /**
     * Changes what a guardian is to this child, or which of them is rung first.
     *
     * <p><strong>Making a link primary clears the previous primary, in this transaction, before the
     * new one is set.</strong> That order is the whole method, for exactly the reason
     * {@code AcademicSessionService#makeCurrent} is: {@code uq_student_guardian_one_primary} is a
     * <em>partial unique index</em>, and an index cannot be deferred the way a constraint can. So the
     * old link is cleared and that clear is flushed before the new one is set. Flipping the order —
     * or trusting Hibernate to order two dirty entities helpfully — fails on a student's second
     * primary reassignment, not the first, which is not a thing to discover in production.
     *
     * <p>Both writes are in one transaction, so a child is never left with no primary contact: if the
     * second fails, the first rolls back with it.
     */
    @Transactional
    public StudentGuardian updateLink(UUID studentId, UUID linkId, UpdateStudentGuardianRequest request) {
        requireStudent(studentId);
        StudentGuardianLink link = requireLink(studentId, linkId);

        Set<String> changed = new LinkedHashSet<>();
        if (link.getRelation() != request.relation()) {
            changed.add("relation");
        }
        if (link.isPrimary() != request.primary()) {
            changed.add("primary");
        }
        if (changed.isEmpty()) {
            return StudentGuardian.of(link);
        }

        if (request.primary() && !link.isPrimary()) {
            clearExistingPrimary(studentId, linkId);
        }

        link.setRelation(request.relation());
        link.setPrimary(request.primary());
        links.saveAndFlush(link);

        audit.recordChange(AuditAction.ENTITY_UPDATED, StudentAudit.STUDENT_GUARDIAN, studentId.toString(), changed);

        return StudentGuardian.of(link);
    }

    /**
     * Detaches a guardian from a child. <strong>The only delete in this module.</strong>
     *
     * <p>It is here because a guardian wrongly attached to a child has to be detachable, and nothing
     * else answers that. Setting a flag would leave the wrong person on the record for anyone reading
     * the table; changing the link's guardian would be "this child's father is actually someone else"
     * performed as a field edit, which is not a correction anyone should make without noticing.
     *
     * <p><strong>What is deleted is a link, not a person.</strong> The {@link Guardian} row survives
     * untouched, along with every other child they are linked to — which is what makes this delete
     * safe where a delete on a student or a guardian would not be. Those two do not exist and are not
     * going to (ADR-0020 §6): fees, attendance and marks reference a student, a school is legally
     * required to produce these records years later, and erasure under the DPDP Act is a different
     * operation with its own design that a {@code DELETE} endpoint was never an answer to.
     *
     * <p>The removal is audited against the student, so the child's history shows that a guardian was
     * detached and by whom. It records field names only, so it does not say <em>which</em> guardian —
     * that name is Confidential, and the audit log is read by more people than the student record is.
     */
    @Transactional
    public void unlink(UUID studentId, UUID linkId) {
        requireStudent(studentId);
        StudentGuardianLink link = requireLink(studentId, linkId);

        links.delete(link);
        links.flush();

        audit.recordChange(
                AuditAction.ENTITY_DELETED,
                StudentAudit.STUDENT_GUARDIAN,
                studentId.toString(),
                List.of("guardianId", "relation", "primary"));
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────

    /**
     * Clears whichever link currently holds primary for this student, and flushes.
     *
     * @param except the link about to be made primary, so that a link already holding it is not
     *     cleared and re-set — which would be two writes saying nothing and, on the same row, a
     *     needless trip through a state the index has an opinion about
     */
    private void clearExistingPrimary(UUID studentId, UUID except) {
        links.findFirstByStudentIdAndPrimaryTrue(studentId).ifPresent(existing -> {
            if (existing.getId().equals(except)) {
                return;
            }
            existing.setPrimary(false);
            links.saveAndFlush(existing);

            // The link that stopped being primary changed too, and the audit log is indexed by
            // entity id: without this row, a child's history would show two guardians becoming the
            // first contact and neither ever stopping.
            audit.recordChange(
                    AuditAction.ENTITY_UPDATED,
                    StudentAudit.STUDENT_GUARDIAN,
                    studentId.toString(),
                    List.of("primary"));
        });
    }

    private Map<UUID, Long> linkedStudentCounts(List<UUID> guardianIds) {
        if (guardianIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : links.countLinkedStudents(guardianIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    /**
     * Name, phone or email, lower-cased on both sides and escaped.
     *
     * <p>Escaped for the reason {@code StudentQueries} escapes: a clerk typing a per-cent sign should
     * search for one, not receive every guardian in the school.
     */
    private static Specification<Guardian> matching(String q) {
        if (q == null || q.isBlank()) {
            return (root, criteria, builder) -> builder.conjunction();
        }
        String pattern = "%" + escapeForLike(q.trim().toLowerCase()) + "%";
        return (root, criteria, builder) -> {
            Predicate byName = builder.like(builder.lower(root.get("fullName")), pattern, '\\');
            Predicate byPhone = builder.like(builder.lower(root.get("phone")), pattern, '\\');
            Predicate byEmail = builder.like(builder.lower(root.get("email")), pattern, '\\');
            return builder.or(byName, byPhone, byEmail);
        };
    }

    private static String escapeForLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String trimmedOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Student requireStudent(UUID id) {
        return students.findById(id).orElseThrow(() -> new NotFoundException("Student", id));
    }

    private Guardian requireGuardian(UUID id) {
        return guardians.findById(id).orElseThrow(() -> new NotFoundException("Guardian", id));
    }

    private StudentGuardianLink requireLink(UUID studentId, UUID linkId) {
        return links.findByIdAndStudentId(linkId, studentId)
                .orElseThrow(() -> new NotFoundException("Guardian link", linkId));
    }
}
