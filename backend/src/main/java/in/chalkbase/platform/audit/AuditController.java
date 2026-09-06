package in.chalkbase.platform.audit;

import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.api.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading this school's audit log (ADR-0018 §6).
 *
 * <p><strong>One method, and it is a GET.</strong> There is no POST, PUT, PATCH or DELETE on this
 * resource and adding one is not a feature request that can be accepted: an audit log an
 * administrator can edit is a log that says whatever the last person to be embarrassed by it wanted
 * it to say. Retention is a scheduled platform job, not an API.
 *
 * <p>The permission is a literal rather than a reference to {@link AuditPermissions}, because a
 * constant in an annotation must be a compile-time constant and an inlined one would survive a
 * rename no better. {@code ControllerAuthorizationTests} is what catches a typo: it checks every
 * code named here against the catalogue.
 *
 * <p>It lives in {@code platform} rather than in a feature module because the audit log is not
 * about any one part of the school — it is the record of all of them, and putting it in {@code
 * identity} or {@code school} would make every other module depend on that one to be audited.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    /** 25 rows, newest first — offset pagination as settled in the Phase 0 decisions. */
    private static final int DEFAULT_PAGE_SIZE = 25;

    private final AuditReader audit;

    public AuditController(AuditReader audit) {
        this.audit = audit;
    }

    /**
     * A page of audit events, newest first unless the caller sorts otherwise.
     *
     * <p>{@code ?page=0&size=25&sort=occurredAt,desc}, plus the filters. Dates are ISO-8601
     * instants: {@code from} is inclusive and {@code to} exclusive, so two consecutive ranges
     * neither overlap nor skip a row.
     */
    @PreAuthorize("hasAuthority('platform:audit:read')")
    @GetMapping
    public ApiResponse<PageResponse<AuditEventResponse>> search(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "occurredAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.success(audit.search(new AuditQuery(actorId, action, from, to), pageable));
    }
}
