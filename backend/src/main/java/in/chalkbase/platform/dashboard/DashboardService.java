package in.chalkbase.platform.dashboard;

import in.chalkbase.platform.audit.AuditEventResponse;
import in.chalkbase.platform.audit.AuditPermissions;
import in.chalkbase.platform.audit.AuditQuery;
import in.chalkbase.platform.audit.AuditReader;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles {@link DashboardResponse} for whoever is signed in, tile by tile, by their own
 * permissions.
 *
 * <p><strong>Imports no feature module.</strong> {@link AcademicsDashboardContributor} and
 * {@link StudentDashboardContributor} are platform-owned SPIs, collected the same way
 * {@code platform.navigation.NavigationCatalog} collects {@code NavigationProvider} beans — each
 * module answers for its own tile, using the permission constants it already owns, and platform
 * never hardcodes another module's permission string or reaches into its {@code api} package. The
 * one direct dependency here is {@link AuditReader}, which is same-module ({@code platform.audit})
 * and therefore an ordinary dependency rather than an SPI.
 *
 * <p><strong>Permission checks happen per tile, not per endpoint.</strong>
 * {@code DashboardController} allows any authenticated caller in (mirroring
 * {@code identity.api.MeController}); this class reads the caller's granted authorities off the
 * security context — the same authorities {@code hasAuthority(...)} checks — and passes them to
 * each contributor, which decides for itself what its own permissions allow it to answer.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** How many recent audit rows the tile carries — enough to read, not a second audit screen. */
    static final int RECENT_AUDIT_LIMIT = 5;

    private final AcademicsDashboardContributor academics;
    private final StudentDashboardContributor students;
    private final AuditReader auditReader;

    public DashboardService(
            AcademicsDashboardContributor academics, StudentDashboardContributor students, AuditReader auditReader) {
        this.academics = academics;
        this.students = students;
        this.auditReader = auditReader;
    }

    public DashboardResponse forCurrentCaller() {
        Set<String> held = heldPermissions();

        SessionTile session = academics.sessionTile(held).orElse(null);
        StudentsTile studentsTile = students.studentsTile(held).orElse(null);
        LinkageGapsTile linkageGaps = students.linkageGapsTile(held).orElse(null);
        RecentAuditTile recentAudit = held.contains(AuditPermissions.AUDIT_READ) ? recentAuditTile() : null;

        return new DashboardResponse(session, studentsTile, linkageGaps, recentAudit);
    }

    private RecentAuditTile recentAuditTile() {
        List<AuditEventResponse> events = auditReader
                .search(
                        AuditQuery.all(),
                        PageRequest.of(0, RECENT_AUDIT_LIMIT, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .content();
        return new RecentAuditTile(events);
    }

    /**
     * The permission codes the current security context grants — the same authorities
     * {@code hasAuthority(...)} checks in every {@code @PreAuthorize} in the codebase, read
     * directly because this decision is per tile rather than per endpoint. An unauthenticated
     * caller never reaches this: {@code DashboardController} requires {@code isAuthenticated()}.
     */
    private static Set<String> heldPermissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
