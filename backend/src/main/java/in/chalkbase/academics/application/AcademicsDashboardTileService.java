package in.chalkbase.academics.application;

import in.chalkbase.academics.domain.AcademicSession;
import in.chalkbase.academics.infrastructure.AcademicSessionRepository;
import in.chalkbase.academics.infrastructure.AcademicsPermissions;
import in.chalkbase.platform.dashboard.AcademicsDashboardContributor;
import in.chalkbase.platform.dashboard.SessionTile;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers {@link AcademicsDashboardContributor} from this module's own repository — the same row
 * {@link AcademicsLookupService#currentSession()} reads.
 *
 * <p>Kept apart from {@link AcademicsLookupService} for the reason that one is itself kept apart
 * from {@link AcademicSessionService}: a change made for one screen must not quietly change what
 * another caller sees, and a dashboard tile is exactly screen-shaped.
 */
@Service
@Transactional(readOnly = true)
class AcademicsDashboardTileService implements AcademicsDashboardContributor {

    private final AcademicSessionRepository sessions;

    AcademicsDashboardTileService(AcademicSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    public Optional<SessionTile> sessionTile(Set<String> heldPermissions) {
        if (!heldPermissions.contains(AcademicsPermissions.SESSION_READ)) {
            return Optional.empty();
        }
        return Optional.of(sessions.findFirstByCurrentTrue()
                .map(AcademicsDashboardTileService::toTile)
                .orElseGet(() -> new SessionTile(false, null, null, null)));
    }

    private static SessionTile toTile(AcademicSession session) {
        return new SessionTile(true, session.getId(), session.getName(), session.getStartsOn());
    }
}
