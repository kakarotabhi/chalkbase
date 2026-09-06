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
 * The school register: which campuses exist on this deployment (ADR-0011).
 *
 * <p><strong>This is a platform-operator view, not a school one, and it took a test pass against a
 * deployed instance to notice that it was neither.</strong> These endpoints were
 * {@code permitAll()} — a leftover from before identity existed, when onboarding had no caller to
 * authenticate. That made {@code GET /api/schools} world-readable: every school's name, code and
 * PostgreSQL schema name, to anyone who asked. A signed-in principal of one school could enumerate
 * every other school on the deployment, which is the one thing schema-per-tenant exists to prevent.
 *
 * <p>They now require {@code school:school:create}, which <strong>no shipped role template
 * holds</strong> ({@code RoleTemplates} says so explicitly). That permission is standing in for a
 * platform-operator role that does not exist yet — the {@code TODO(identity)} in
 * {@code SecurityConfig} is the real answer, and when it lands these move onto it.
 *
 * <p>The setup key on the {@code prod} profile is a second lock on the same door, not the only one.
 * It was added when the application got a public URL, and defending a cross-tenant read with a
 * filter alone would mean removing the filter re-opens the leak silently.
 */
@RestController
@RequestMapping("/api/schools")
public class SchoolController {

    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @PreAuthorize("hasAuthority('school:school:create')")
    @GetMapping
    public ApiResponse<List<SchoolResponse>> list() {
        return ApiResponse.success(schoolService.findAll());
    }

    @PreAuthorize("hasAuthority('school:school:create')")
    @GetMapping("/{id}")
    public ApiResponse<SchoolResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(schoolService.findById(id));
    }

    @PreAuthorize("hasAuthority('school:school:create')")
    @PostMapping
    public ResponseEntity<ApiResponse<SchoolResponse>> create(@Valid @RequestBody CreateSchoolRequest request) {
        SchoolResponse created = schoolService.create(request);
        return ResponseEntity.created(URI.create("/api/schools/" + created.id()))
                .body(ApiResponse.success(created));
    }
}
