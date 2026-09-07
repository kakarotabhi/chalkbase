package in.chalkbase.school.api;

import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.reference.ReferenceItemResponse;
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
 * deployed instance to notice that it was neither.</strong> {@link #list}, {@link #get} and
 * {@link #create} were {@code permitAll()} — a leftover from before identity existed, when
 * onboarding had no caller to authenticate. That made {@code GET /api/schools} world-readable:
 * every school's name, code and PostgreSQL schema name, to anyone who asked. A signed-in principal
 * of one school could enumerate every other school on the deployment, which is the one thing
 * schema-per-tenant exists to prevent.
 *
 * <p>They now require {@code school:school:create}, which <strong>no shipped role template
 * holds</strong> ({@code RoleTemplates} says so explicitly). Nothing can call them today, and that
 * is intentional rather than a bug still to close: ADR-0024 looked at introducing a
 * platform-operator account to hold that permission and rejected it, for now, as the larger of two
 * fixes for a smaller problem. These three stay reserved for that account if one is ever built.
 *
 * <p>{@link #bootstrap} is the actual fix. It needs no operator principal because it is not an
 * operator action in the ADR-0005 sense — there is nobody to authenticate yet, which is exactly the
 * problem it exists to solve. It is guarded instead by the setup key on the {@code prod} profile
 * ({@code SetupKeyFilter}) and by refusing to run twice for a school that already has an account.
 * See ADR-0024 for the reasoning and the option that was not taken.
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

    /**
     * Every board a school may affiliate to (ADR-0006, ADR-0029) — the other half of the two
     * hardcoded lists the school-profile form used to carry, alongside {@code
     * platform.reference.ReferenceDataController#states}. It lives here rather than there because
     * {@code Board} is this module's own domain enum, and {@code platform} — the shared kernel every
     * module depends on — must not import a feature module's type the other way round.
     *
     * <p>{@code isAuthenticated()}, mirroring {@code ReferenceDataController#states}: this is not
     * one of the three platform-operator endpoints above, and sits under {@code /api/schools/**}'s
     * URL-level {@code permitAll()} only because that is this controller's existing prefix — the
     * method annotation is what actually decides who may call it, and it still refuses an anonymous
     * caller. No shipped screen calls it without a session; the day one does (a public onboarding
     * form, say) this is the annotation to relax, deliberately, not by omission.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/boards")
    public ApiResponse<List<ReferenceItemResponse>> boards() {
        return ApiResponse.success(schoolService.boards());
    }

    @PreAuthorize("hasAuthority('school:school:create')")
    @PostMapping
    public ResponseEntity<ApiResponse<SchoolResponse>> create(@Valid @RequestBody CreateSchoolRequest request) {
        SchoolResponse created = schoolService.create(request);
        return ResponseEntity.created(URI.create("/api/schools/" + created.id()))
                .body(ApiResponse.success(created));
    }

    /**
     * Brings one school online and creates its first administrator, atomically from the caller's
     * point of view (ADR-0024). The only way to onboard a real school today: {@link #create} exists
     * but nothing can reach it, and {@code DemoSchoolSeeder} is a {@code local}-only developer tool.
     *
     * <p>{@code permitAll()} is correct here, not a gap — see the class Javadoc. Guarded on
     * {@code prod} by {@code SetupKeyFilter} ({@code /api/schools/**}) and, unconditionally, by
     * refusing ({@code AUTH_014}) once the school's schema already holds an account.
     */
    @PreAuthorize("permitAll()")
    @PostMapping("/bootstrap")
    public ResponseEntity<ApiResponse<SchoolBootstrapResponse>> bootstrap(
            @Valid @RequestBody BootstrapSchoolRequest request) {
        SchoolBootstrapResponse created = schoolService.bootstrap(request);
        return ResponseEntity.created(
                        URI.create("/api/schools/" + created.school().id()))
                .body(ApiResponse.success(created));
    }
}
