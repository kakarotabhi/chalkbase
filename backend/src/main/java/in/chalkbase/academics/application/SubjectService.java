package in.chalkbase.academics.application;

import in.chalkbase.academics.api.CreateSubjectRequest;
import in.chalkbase.academics.api.SubjectResponse;
import in.chalkbase.academics.api.UpdateSubjectRequest;
import in.chalkbase.academics.domain.AcademicsAudit;
import in.chalkbase.academics.domain.Subject;
import in.chalkbase.academics.infrastructure.SubjectRepository;
import in.chalkbase.platform.api.PageResponse;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The school's catalogue of subjects.
 *
 * <p>A flat list, unlike {@link SchoolClassService}: a subject has no ladder position and nothing
 * here reorders anything. Scoped to the school bound to this request and to no other — the schema
 * is the tenant boundary (ADR-0011), so an id belonging to another school is simply absent from
 * this schema and answered with a 404.
 *
 * <p><strong>Nothing here deletes.</strong> A subject is deactivated, for the reason
 * {@link Subject} gives: the timetable and marks modules that come next will reference one, and by
 * then it is too late to decide that deleting it was wrong.
 */
@Service
@Transactional(readOnly = true)
public class SubjectService {

    private final SubjectRepository subjects;
    private final AuditService audit;

    public SubjectService(SubjectRepository subjects, AuditService audit) {
        this.subjects = subjects;
        this.audit = audit;
    }

    /**
     * One page of the catalogue, active and inactive alike.
     *
     * <p>Inactive rows are not filtered out, for the same reason {@code SchoolClassService#list}
     * keeps them: hiding a retired subject here would hide it from the one screen able to bring it
     * back, and a name that looks free because the row holding it is dark is exactly the trap
     * {@code uq_subject_name} does not know how to avoid (see {@code Subject}).
     */
    public PageResponse<SubjectResponse> list(String q, Pageable pageable) {
        Page<Subject> page = subjects.findAll(matching(q), pageable);
        return PageResponse.of(
                page, page.getContent().stream().map(SubjectResponse::of).toList());
    }

    @Transactional
    public SubjectResponse create(CreateSubjectRequest request) {
        Subject created = subjects.saveAndFlush(
                new Subject(request.name().trim(), request.code().trim()));

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                AcademicsAudit.SUBJECT,
                created.getId().toString(),
                List.of("name", "code"));

        return SubjectResponse.of(created);
    }

    /** Renames, recodes, retires or reinstates a subject. */
    @Transactional
    public SubjectResponse update(UUID id, UpdateSubjectRequest request) {
        Subject subject = requireSubject(id);
        String name = request.name().trim();
        String code = request.code().trim();

        Set<String> changed = new LinkedHashSet<>();
        if (!Objects.equals(subject.getName(), name)) {
            changed.add("name");
        }
        if (!Objects.equals(subject.getCode(), code)) {
            changed.add("code");
        }
        if (subject.isActive() != request.active()) {
            changed.add("active");
        }
        if (changed.isEmpty()) {
            return SubjectResponse.of(subject);
        }

        subject.rename(name, code);
        subject.setActive(request.active());
        subjects.saveAndFlush(subject);

        audit.recordChange(
                AuditAction.ENTITY_UPDATED,
                AcademicsAudit.SUBJECT,
                subject.getId().toString(),
                changed);

        return SubjectResponse.of(subject);
    }

    private Subject requireSubject(UUID id) {
        return subjects.findById(id).orElseThrow(() -> new NotFoundException("Subject", id));
    }

    /**
     * Free text over name and code, the same shape {@code GuardianRepository}'s search uses: a
     * case-insensitive substring match, empty rather than refused for a blank query.
     */
    private static Specification<Subject> matching(String q) {
        if (q == null || q.isBlank()) {
            return (root, criteria, builder) -> builder.conjunction();
        }
        String pattern = "%" + escapeForLike(q.trim().toLowerCase()) + "%";
        return (root, criteria, builder) -> {
            List<Predicate> alternatives = new ArrayList<>();
            alternatives.add(builder.like(builder.lower(root.get("name")), pattern, '\\'));
            alternatives.add(builder.like(builder.lower(root.get("code")), pattern, '\\'));
            return builder.or(alternatives.toArray(new Predicate[0]));
        };
    }

    private static String escapeForLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
