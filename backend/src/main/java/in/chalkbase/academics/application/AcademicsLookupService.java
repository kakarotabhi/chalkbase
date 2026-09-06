package in.chalkbase.academics.application;

import in.chalkbase.academics.api.AcademicSessionRef;
import in.chalkbase.academics.api.AcademicsLookup;
import in.chalkbase.academics.api.SectionRef;
import in.chalkbase.academics.infrastructure.AcademicSessionRepository;
import in.chalkbase.academics.infrastructure.SectionRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What {@link AcademicsLookup} promises, answered from this module's own repositories.
 *
 * <p>Read-only throughout, and the class carries no {@code @Transactional} write anywhere: the
 * interface exists so another module can <em>resolve</em> the structure it points at, never so it
 * can change it.
 *
 * <p>Separate from {@link AcademicSessionService} and {@link SchoolClassService} rather than folded
 * into them, because those two are the API's read and write models for a screen and will grow with
 * it. This one answers a different question — "what is this id" — for callers outside the module,
 * and keeping it apart means a change made for a screen cannot quietly change what another module
 * sees.
 *
 * <p>Tenant-scoped like everything else: no school argument, because the schema is the boundary
 * (ADR-0011). An id from another school is not in this schema, so it resolves to empty rather than
 * to a leak.
 */
@Service
@Transactional(readOnly = true)
public class AcademicsLookupService implements AcademicsLookup {

    private final AcademicSessionRepository sessions;
    private final SectionRepository sections;

    public AcademicsLookupService(AcademicSessionRepository sessions, SectionRepository sections) {
        this.sessions = sessions;
        this.sections = sections;
    }

    @Override
    public Optional<AcademicSessionRef> currentSession() {
        return sessions.findFirstByCurrentTrue().map(AcademicSessionRef::of);
    }

    @Override
    public Optional<AcademicSessionRef> session(UUID sessionId) {
        return sessionId == null
                ? Optional.empty()
                : sessions.findById(sessionId).map(AcademicSessionRef::of);
    }

    @Override
    public Optional<SectionRef> section(UUID sectionId) {
        return sectionId == null
                ? Optional.empty()
                : sections.findWithClassById(sectionId).map(SectionRef::of);
    }

    @Override
    public Map<UUID, AcademicSessionRef> sessions(Collection<UUID> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        return sessions.findAllById(distinct(sessionIds)).stream()
                .map(AcademicSessionRef::of)
                .collect(Collectors.toMap(AcademicSessionRef::id, Function.identity()));
    }

    @Override
    public Map<UUID, SectionRef> sections(Collection<UUID> sectionIds) {
        if (sectionIds == null || sectionIds.isEmpty()) {
            return Map.of();
        }
        return sections.findAllWithClassByIdIn(distinct(sectionIds)).stream()
                .map(SectionRef::of)
                .collect(Collectors.toMap(SectionRef::id, Function.identity()));
    }

    /**
     * De-duplicated and null-free, because a caller assembling ids from a page of rows will have
     * repeats — thirty students in one section is one section id thirty times — and a nullable
     * column contributes nulls that {@code in (...)} has no use for.
     */
    private static Collection<UUID> distinct(Collection<UUID> ids) {
        return ids.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
