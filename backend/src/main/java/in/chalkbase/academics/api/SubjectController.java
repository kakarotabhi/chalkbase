package in.chalkbase.academics.api;

import in.chalkbase.academics.application.SubjectService;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.api.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * This school's subject catalogue.
 *
 * <p>Paged, unlike {@code /api/academics/classes} and {@code /api/academics/sessions}: a subject
 * catalogue can genuinely run into dozens of rows for a CBSE school offering electives at Classes
 * 9-12, where a class ladder is at most a few dozen rungs and a school gains one session a year. So
 * this is the one academics list shaped like {@code GET /api/guardians} rather than like the
 * ladder.
 *
 * <p><strong>There is no DELETE, deliberately</strong> (ADR-0019, and see {@code Subject}). A
 * subject is deactivated: by the time the timetable or marks modules name one, deciding that
 * deleting it was a mistake is too late, and a subject created in error is fixed by editing it.
 *
 * <p>The permission strings are literals for the same reason they are in
 * {@code SchoolClassController}: an annotation needs a compile-time constant.
 * {@code ControllerAuthorizationTests} is what catches a typo.
 */
@RestController
@RequestMapping("/api/academics/subjects")
public class SubjectController {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final SubjectService subjects;

    public SubjectController(SubjectService subjects) {
        this.subjects = subjects;
    }

    /**
     * One page of the catalogue, by name unless the caller sorts otherwise.
     *
     * <p>Inactive rows come back too, flagged. Filtering them here would hide a retired subject
     * from the one screen able to bring it back.
     *
     * @param q free text over name and code
     */
    @PreAuthorize("hasAuthority('academics:subject:read')")
    @GetMapping
    public ApiResponse<PageResponse<SubjectResponse>> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "name") Pageable pageable) {
        return ApiResponse.success(subjects.list(q, pageable));
    }

    @PreAuthorize("hasAuthority('academics:subject:manage')")
    @PostMapping
    public ResponseEntity<ApiResponse<SubjectResponse>> create(@Valid @RequestBody CreateSubjectRequest request) {
        SubjectResponse created = subjects.create(request);
        return ResponseEntity.created(URI.create("/api/academics/subjects/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @PreAuthorize("hasAuthority('academics:subject:manage')")
    @PutMapping("/{id}")
    public ApiResponse<SubjectResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateSubjectRequest request) {
        return ApiResponse.success(subjects.update(id, request));
    }
}
