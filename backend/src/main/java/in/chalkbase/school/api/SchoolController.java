package in.chalkbase.school.api;

import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.school.application.SchoolService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The school register: which campuses exist, and onboarding a new one.
 *
 * <p><strong>Every method here is deliberately open</strong>, and each says so with an explicit
 * {@code @PreAuthorize("permitAll()")} rather than by being absent from the check. Onboarding a
 * campus is a platform-operator action performed before any account exists inside the school, and
 * there are no platform-operator accounts yet — a support user is its own thing, a platform-level
 * role outside any school, audited and time-boxed (ADR-0005), and building it is not this change.
 *
 * <p>The permissions the register will be guarded by are already declared, in
 * {@code SchoolPermissions}, and the shipped role templates already hold {@code school:school:read}.
 * What is missing is only the principal to check them against.
 *
 * <p>On the {@code prod} profile these methods are not reachable without the
 * {@code X-Chalkbase-Setup-Key} header — {@code platform.config.SetupKeyFilter} answers 404 to
 * anything else. That is a stopgap added because the application now has a public URL and
 * {@code create} provisions a PostgreSQL schema; a single shared secret names nobody and audits as
 * nobody, so it is not the operator account and does not close the TODO below.
 *
 * <p>TODO(identity): replace {@code permitAll()} with
 * {@code hasAuthority(SchoolPermissions.SCHOOL_READ)} and {@code ...SCHOOL_CREATE} in the same
 * change that introduces platform-operator accounts, and remove the matching exemptions from
 * {@code SecurityConfig} — and the setup key with them.
 */
@RestController
@RequestMapping("/api/schools")
public class SchoolController {

    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @PreAuthorize("permitAll()")
    @GetMapping
    public ApiResponse<List<SchoolResponse>> list() {
        return ApiResponse.success(schoolService.findAll());
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public ApiResponse<SchoolResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(schoolService.findById(id));
    }

    @PreAuthorize("permitAll()")
    @PostMapping
    public ResponseEntity<ApiResponse<SchoolResponse>> create(@Valid @RequestBody CreateSchoolRequest request) {
        SchoolResponse created = schoolService.create(request);
        return ResponseEntity.created(URI.create("/api/schools/" + created.id()))
                .body(ApiResponse.success(created));
    }
}
