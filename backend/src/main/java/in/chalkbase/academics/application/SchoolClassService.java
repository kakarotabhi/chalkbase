package in.chalkbase.academics.application;

import in.chalkbase.academics.api.CreateSchoolClassRequest;
import in.chalkbase.academics.api.CreateSectionRequest;
import in.chalkbase.academics.api.ReorderSchoolClassesRequest;
import in.chalkbase.academics.api.SchoolClassResponse;
import in.chalkbase.academics.api.SectionResponse;
import in.chalkbase.academics.api.UpdateSchoolClassRequest;
import in.chalkbase.academics.api.UpdateSectionRequest;
import in.chalkbase.academics.domain.AcademicsAudit;
import in.chalkbase.academics.domain.AcademicsErrorCode;
import in.chalkbase.academics.domain.SchoolClass;
import in.chalkbase.academics.domain.Section;
import in.chalkbase.academics.infrastructure.SchoolClassRepository;
import in.chalkbase.academics.infrastructure.SectionRepository;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.ChalkbaseException;
import in.chalkbase.platform.error.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The school's ladder of classes, and the sections inside them.
 *
 * <p>Structural rather than per session (ADR-0019): there is one Class 5 row, not one per academic
 * year, and no session id appears anywhere here.
 *
 * <p>Scoped to the school bound to this request and to no other — the schema is the tenant boundary
 * (ADR-0011), so an id belonging to another school is simply absent from this schema and answered
 * with a 404.
 *
 * <p><strong>Nothing here deletes.</strong> Classes and sections are deactivated, because by the
 * time an enrolment names one, deciding that deleting it was a mistake is too late. A class created
 * by accident is fixed by renaming it.
 */
@Service
@Transactional(readOnly = true)
public class SchoolClassService {

    private final SchoolClassRepository classes;
    private final SectionRepository sections;
    private final AuditService audit;

    public SchoolClassService(SchoolClassRepository classes, SectionRepository sections, AuditService audit) {
        this.classes = classes;
        this.sections = sections;
        this.audit = audit;
    }

    /** The whole ladder in display order, sections and all, active and inactive alike. */
    public List<SchoolClassResponse> list() {
        return classes.findAllWithSections().stream()
                .map(SchoolClassResponse::of)
                .toList();
    }

    /**
     * Appends a class at the end of the ladder.
     *
     * <p>{@code max(sequence) + 1}, or 1 for a school that has none yet — nothing is seeded,
     * because schools genuinely disagree about where their ladder starts and ends (ADR-0019).
     *
     * <p>Two creates racing here compute the same next position and the second is answered with a
     * conflict from {@code uq_school_class_sequence}. That is the right trade: serialising every
     * class creation to avoid a collision a school meets once, if ever, would cost more than
     * retrying does.
     */
    @Transactional
    public SchoolClassResponse create(CreateSchoolClassRequest request) {
        int next = classes.highestSequence().orElse(0) + 1;
        SchoolClass created =
                classes.saveAndFlush(new SchoolClass(request.name().trim(), next));

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                AcademicsAudit.SCHOOL_CLASS,
                created.getId().toString(),
                List.of("name", "sequence"));

        return SchoolClassResponse.of(created);
    }

    /** Renames a class, retires it, or brings it back. Never moves it: that is {@link #reorder}. */
    @Transactional
    public SchoolClassResponse update(UUID id, UpdateSchoolClassRequest request) {
        SchoolClass schoolClass = requireClass(id);
        String name = request.name().trim();

        Set<String> changed = new LinkedHashSet<>();
        if (!Objects.equals(schoolClass.getName(), name)) {
            changed.add("name");
        }
        if (schoolClass.isActive() != request.active()) {
            changed.add("active");
        }
        if (changed.isEmpty()) {
            return SchoolClassResponse.of(schoolClass);
        }

        schoolClass.rename(name);
        schoolClass.setActive(request.active());
        classes.saveAndFlush(schoolClass);

        audit.recordChange(
                AuditAction.ENTITY_UPDATED,
                AcademicsAudit.SCHOOL_CLASS,
                schoolClass.getId().toString(),
                changed);

        return SchoolClassResponse.of(schoolClass);
    }

    /**
     * Renumbers the whole ladder from a list of ids, in one transaction.
     *
     * <p>The list must be exactly this school's classes — each one once, none missing, none
     * unknown. That check is the point of the endpoint rather than a guard on it: a client that
     * dropped one id would otherwise have its remaining classes renumbered into a shorter ladder,
     * and the missing rung would go unnoticed until somebody tried to enrol into it.
     *
     * <p>Assigning every position in one transaction is what
     * {@code uq_school_class_sequence deferrable initially deferred} is for. Swapping two classes
     * passes through a state where both hold the same number; deferred means the database checks
     * once, at commit, when the ladder is whole again. Two separate updates could not do this
     * without a temporary value nobody wants in the table.
     */
    @Transactional
    public List<SchoolClassResponse> reorder(ReorderSchoolClassesRequest request) {
        List<SchoolClass> existing = classes.findAll();
        List<UUID> order = request.classIds();
        rejectAnythingButACompleteOrder(existing, order);

        Map<UUID, SchoolClass> byId =
                existing.stream().collect(Collectors.toMap(SchoolClass::getId, schoolClass -> schoolClass));

        List<SchoolClass> moved = new ArrayList<>();
        for (int position = 0; position < order.size(); position++) {
            SchoolClass schoolClass = byId.get(order.get(position));
            int sequence = position + 1;
            if (schoolClass.getSequence() != sequence) {
                schoolClass.moveTo(sequence);
                moved.add(schoolClass);
            }
        }
        classes.saveAll(moved);
        classes.flush();

        // One row per class that actually moved, and none at all for a list that was already in
        // this order. A class's own history is what the audit log is indexed for, so a single row
        // saying "the ladder was reordered" would answer "what happened to Class 5" with nothing.
        for (SchoolClass schoolClass : moved) {
            audit.recordChange(
                    AuditAction.ENTITY_UPDATED,
                    AcademicsAudit.SCHOOL_CLASS,
                    schoolClass.getId().toString(),
                    List.of("sequence"));
        }

        return list();
    }

    /** A new division of one class. The class comes from the path; a section outside one is not a thing. */
    @Transactional
    public SectionResponse addSection(UUID classId, CreateSectionRequest request) {
        SchoolClass owner = requireClass(classId);
        Section section =
                sections.saveAndFlush(new Section(owner, request.name().trim()));

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                AcademicsAudit.SECTION,
                section.getId().toString(),
                List.of("name", "schoolClassId"));

        return SectionResponse.of(section);
    }

    /** Renames a section, retires it, or brings it back. Never moves it to another class. */
    @Transactional
    public SectionResponse updateSection(UUID id, UpdateSectionRequest request) {
        Section section = sections.findById(id).orElseThrow(() -> new NotFoundException("Section", id));
        String name = request.name().trim();

        Set<String> changed = new LinkedHashSet<>();
        if (!Objects.equals(section.getName(), name)) {
            changed.add("name");
        }
        if (section.isActive() != request.active()) {
            changed.add("active");
        }
        if (changed.isEmpty()) {
            return SectionResponse.of(section);
        }

        section.rename(name);
        section.setActive(request.active());
        sections.saveAndFlush(section);

        audit.recordChange(
                AuditAction.ENTITY_UPDATED,
                AcademicsAudit.SECTION,
                section.getId().toString(),
                changed);

        return SectionResponse.of(section);
    }

    // ── internals ────────────────────────────────────────────────────────────────────────────

    /**
     * Refuses a reorder that is not a permutation of this school's classes.
     *
     * <p>All three failures are reported at once and by name, because a client sent one list and
     * fixing it one complaint at a time is three round trips. The ids are echoed rather than
     * counted: they are the client's own, they identify nothing about a person, and a developer
     * looking at "missing: <id>" knows immediately what their screen dropped.
     */
    private static void rejectAnythingButACompleteOrder(List<SchoolClass> existing, List<UUID> order) {
        Set<UUID> known =
                existing.stream().map(SchoolClass::getId).collect(Collectors.toCollection(LinkedHashSet::new));

        Set<UUID> seen = new LinkedHashSet<>();
        Set<UUID> duplicated = new LinkedHashSet<>();
        Set<UUID> unknown = new LinkedHashSet<>();
        for (UUID id : order) {
            if (!seen.add(id)) {
                duplicated.add(id);
            }
            if (!known.contains(id)) {
                unknown.add(id);
            }
        }
        Set<UUID> missing = new LinkedHashSet<>(known);
        missing.removeAll(seen);

        if (duplicated.isEmpty() && unknown.isEmpty() && missing.isEmpty()) {
            return;
        }
        Map<String, String> offending = new LinkedHashMap<>();
        describe(offending, "missing", missing);
        describe(offending, "duplicated", duplicated);
        describe(offending, "unknown", unknown);
        throw new ChalkbaseException(
                AcademicsErrorCode.INCOMPLETE_CLASS_ORDER,
                AcademicsErrorCode.INCOMPLETE_CLASS_ORDER.defaultMessage(),
                offending);
    }

    private static void describe(Map<String, String> offending, String what, Set<UUID> ids) {
        if (!ids.isEmpty()) {
            offending.put(what, ids.stream().map(UUID::toString).collect(Collectors.joining(", ")));
        }
    }

    private SchoolClass requireClass(UUID id) {
        return classes.findById(id).orElseThrow(() -> new NotFoundException("Class", id));
    }
}
