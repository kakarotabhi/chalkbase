package in.chalkbase.academics.api;

import in.chalkbase.academics.application.SchoolClassService;
import in.chalkbase.platform.api.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This school's ladder of classes and the sections inside them.
 *
 * <p>Sections are here rather than on a controller of their own because they are never useful
 * apart from their class: one screen shows the ladder, and editing a section is something done on
 * it. Creating one is addressed through its class ({@code /classes/{id}/sections}); editing one
 * already knows which class it belongs to, so it is addressed by its own id.
 *
 * <p><strong>There is no DELETE anywhere here, deliberately</strong> (ADR-0019). A class or a
 * section is deactivated: by the time an enrolment names one, deciding that deleting it was a
 * mistake is too late, and a class created in error is fixed by renaming it.
 *
 * <p>The permission strings are literals for the same reason they are in
 * {@code SchoolProfileController}: an annotation needs a compile-time constant.
 * {@code ControllerAuthorizationTests} is what catches a typo.
 */
@RestController
@RequestMapping("/api/academics")
public class SchoolClassController {

    private final SchoolClassService classes;

    public SchoolClassController(SchoolClassService classes) {
        this.classes = classes;
    }

    /**
     * The whole ladder in {@code sequence} order, each class carrying its sections by name.
     *
     * <p>Inactive rows come back too, flagged. Filtering them here would hide a retired class from
     * the one screen able to bring it back.
     */
    @PreAuthorize("hasAuthority('academics:class:read')")
    @GetMapping("/classes")
    public ApiResponse<List<SchoolClassResponse>> list() {
        return ApiResponse.success(classes.list());
    }

    @PreAuthorize("hasAuthority('academics:class:manage')")
    @PostMapping("/classes")
    public ResponseEntity<ApiResponse<SchoolClassResponse>> create(
            @Valid @RequestBody CreateSchoolClassRequest request) {
        SchoolClassResponse created = classes.create(request);
        return ResponseEntity.created(URI.create("/api/academics/classes/" + created.id()))
                .body(ApiResponse.success(created));
    }

    /**
     * Renumbers the whole ladder in one transaction.
     *
     * <p>Mapped before {@code /classes/{id}} in this file for readability only — Spring's path
     * matching prefers the literal segment over the template regardless of declaration order.
     */
    @PreAuthorize("hasAuthority('academics:class:manage')")
    @PutMapping("/classes/order")
    public ApiResponse<List<SchoolClassResponse>> reorder(@Valid @RequestBody ReorderSchoolClassesRequest request) {
        return ApiResponse.success(classes.reorder(request));
    }

    @PreAuthorize("hasAuthority('academics:class:manage')")
    @PutMapping("/classes/{id}")
    public ApiResponse<SchoolClassResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateSchoolClassRequest request) {
        return ApiResponse.success(classes.update(id, request));
    }

    @PreAuthorize("hasAuthority('academics:class:manage')")
    @PostMapping("/classes/{id}/sections")
    public ResponseEntity<ApiResponse<SectionResponse>> addSection(
            @PathVariable UUID id, @Valid @RequestBody CreateSectionRequest request) {
        SectionResponse created = classes.addSection(id, request);
        return ResponseEntity.created(URI.create("/api/academics/sections/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @PreAuthorize("hasAuthority('academics:class:manage')")
    @PutMapping("/sections/{id}")
    public ApiResponse<SectionResponse> updateSection(
            @PathVariable UUID id, @Valid @RequestBody UpdateSectionRequest request) {
        return ApiResponse.success(classes.updateSection(id, request));
    }
}
