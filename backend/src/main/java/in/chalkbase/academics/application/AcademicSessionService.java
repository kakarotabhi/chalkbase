package in.chalkbase.academics.application;

import in.chalkbase.academics.api.AcademicSessionResponse;
import in.chalkbase.academics.api.SaveAcademicSessionRequest;
import in.chalkbase.academics.domain.AcademicSession;
import in.chalkbase.academics.domain.AcademicsAudit;
import in.chalkbase.academics.infrastructure.AcademicSessionRepository;
import in.chalkbase.platform.audit.AuditAction;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.error.NotFoundException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The school's academic years, and which one it is in.
 *
 * <p>Every method here works in the school bound to this request and can reach no other: the
 * connection's {@code search_path} is what selects the schema (ADR-0011), so there is no school id
 * to pass and none to get wrong. An id from another school is not in this schema, which makes it a
 * 404 rather than a leak.
 *
 * <p>Audited per ADR-0018: each write records the NAMES of the fields it changed, in the same
 * transaction as the change, and records nothing when nothing differed.
 */
@Service
@Transactional(readOnly = true)
public class AcademicSessionService {

    private final AcademicSessionRepository sessions;
    private final AuditService audit;

    public AcademicSessionService(AcademicSessionRepository sessions, AuditService audit) {
        this.sessions = sessions;
        this.audit = audit;
    }

    /** Newest first, unpaged: a school gains one of these a year. */
    public List<AcademicSessionResponse> list() {
        return sessions.findAllByOrderByStartsOnDescNameAsc().stream()
                .map(AcademicSessionResponse::of)
                .toList();
    }

    /**
     * A new academic year, not current until somebody says so.
     *
     * <p>Created outside the current year on purpose. A school setting up next year in February
     * would otherwise move itself into it the moment it pressed Save.
     */
    @Transactional
    public AcademicSessionResponse create(SaveAcademicSessionRequest request) {
        AcademicSession session =
                sessions.saveAndFlush(new AcademicSession(request.name().trim(), request.startsOn(), request.endsOn()));

        audit.recordChange(
                AuditAction.ENTITY_CREATED,
                AcademicsAudit.ACADEMIC_SESSION,
                session.getId().toString(),
                List.of("name", "startsOn", "endsOn"));

        return AcademicSessionResponse.of(session);
    }

    /** Renames a year or corrects its dates. Never touches which year is current. */
    @Transactional
    public AcademicSessionResponse update(UUID id, SaveAcademicSessionRequest request) {
        AcademicSession session = require(id);
        String name = request.name().trim();

        // Diffed before the entity is mutated, because afterwards there is nothing to compare
        // against — the same reason SchoolProfileService does it in that order.
        Set<String> changed = new LinkedHashSet<>();
        if (!Objects.equals(session.getName(), name)) {
            changed.add("name");
        }
        if (!Objects.equals(session.getStartsOn(), request.startsOn())) {
            changed.add("startsOn");
        }
        if (!Objects.equals(session.getEndsOn(), request.endsOn())) {
            changed.add("endsOn");
        }
        if (changed.isEmpty()) {
            // A form resubmitted unchanged is the commonest way to fill an audit log with rows that
            // say nothing. Nothing is written, and nothing is recorded.
            return AcademicSessionResponse.of(session);
        }

        session.apply(name, request.startsOn(), request.endsOn());
        sessions.saveAndFlush(session);

        audit.recordChange(
                AuditAction.ENTITY_UPDATED,
                AcademicsAudit.ACADEMIC_SESSION,
                session.getId().toString(),
                changed);

        return AcademicSessionResponse.of(session);
    }

    /**
     * Moves the school into this academic year, and out of whichever one it was in.
     *
     * <p><strong>The order of the two writes is the whole method.</strong>
     * {@code uq_academic_session_one_current} is a <em>partial unique index</em>, and an index
     * cannot be deferred the way {@code uq_school_class_sequence} can. So the previous session is
     * cleared and that clear is flushed to the database <em>before</em> the new one is set;
     * flipping the order — or relying on Hibernate to order two dirty entities helpfully — fails
     * with a conflict on a school's second-ever session switch, which is not a thing to discover in
     * production.
     *
     * <p>Both writes are in one transaction, so a school is never left with no current session: if
     * the second fails, the first rolls back with it.
     */
    @Transactional
    public List<AcademicSessionResponse> makeCurrent(UUID id) {
        AcademicSession session = require(id);
        if (session.isCurrent()) {
            // Already the current year. Nothing changed, so nothing is written and nothing is
            // audited — a double-click on the button is not an event.
            return list();
        }

        AcademicSession previous = sessions.findFirstByCurrentTrue().orElse(null);
        if (previous != null) {
            previous.stopBeingCurrent();
            sessions.saveAndFlush(previous);
        }

        session.becomeCurrent();
        sessions.saveAndFlush(session);

        if (previous != null) {
            // The session that stopped being current changed too, and the audit log is indexed by
            // entity id: without this row, asking what happened to last year's session would show
            // it becoming current and never stopping.
            audit.recordChange(
                    AuditAction.ENTITY_UPDATED,
                    AcademicsAudit.ACADEMIC_SESSION,
                    previous.getId().toString(),
                    List.of("current"));
        }
        audit.recordChange(
                AcademicsAudit.SESSION_MADE_CURRENT,
                AcademicsAudit.ACADEMIC_SESSION,
                session.getId().toString(),
                List.of("current"));

        // The whole list, not the session that was switched to. This call changes TWO rows — one
        // becomes current and another stops being — and answering with only the winner leaves the
        // caller holding a list in which two sessions claim to be current, with nothing to tell it
        // otherwise. `reorder` returns the full ladder for the same reason: an endpoint that
        // rearranges a set answers with the set.
        return list();
    }

    private AcademicSession require(UUID id) {
        return sessions.findById(id).orElseThrow(() -> new NotFoundException("Academic session", id));
    }
}
