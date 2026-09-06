package in.chalkbase.academics.api;

import in.chalkbase.academics.application.AcademicSessionService;
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
 * This school's academic years.
 *
 * <p>No school id in any path: the session says which school this request works in (ADR-0011), and
 * a parameter saying so again would be a second, weaker answer to a question already answered.
 *
 * <p><strong>There is no DELETE, and that is deliberate</strong> (ADR-0019). A session is the time
 * axis enrolments hang off, so removing one would orphan them; a session created by mistake is
 * fixed by renaming it.
 *
 * <p>The permission strings are literals rather than references to {@code AcademicsPermissions},
 * following {@code SchoolProfileController} and {@code AccessController}: an annotation needs a
 * compile-time constant, and an inlined one survives a rename no better.
 * {@code ControllerAuthorizationTests} is what catches a typo.
 */
@RestController
@RequestMapping("/api/academics/sessions")
public class AcademicSessionController {

    private final AcademicSessionService sessions;

    public AcademicSessionController(AcademicSessionService sessions) {
        this.sessions = sessions;
    }

    /** Newest first. Unpaged on purpose: a school gains one of these a year (ADR-0007 lists pages). */
    @PreAuthorize("hasAuthority('academics:session:read')")
    @GetMapping
    public ApiResponse<List<AcademicSessionResponse>> list() {
        return ApiResponse.success(sessions.list());
    }

    @PreAuthorize("hasAuthority('academics:session:manage')")
    @PostMapping
    public ResponseEntity<ApiResponse<AcademicSessionResponse>> create(
            @Valid @RequestBody SaveAcademicSessionRequest request) {
        AcademicSessionResponse created = sessions.create(request);
        return ResponseEntity.created(URI.create("/api/academics/sessions/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @PreAuthorize("hasAuthority('academics:session:manage')")
    @PutMapping("/{id}")
    public ApiResponse<AcademicSessionResponse> update(
            @PathVariable UUID id, @Valid @RequestBody SaveAcademicSessionRequest request) {
        return ApiResponse.success(sessions.update(id, request));
    }

    /**
     * Moves the school into this year and out of whichever one it was in, in one transaction.
     *
     * <p>Its own endpoint rather than a {@code current} field on the edit form, because the thing
     * being set is mutually exclusive across rows: two edit screens saved a second apart would
     * otherwise disagree about which year the school is in, and the second would be answered with a
     * conflict from a database index rather than with an action it asked for.
     *
     * <p>A POST rather than a PUT: it is not a replacement of a representation, it is an instruction
     * that changes two rows.
     *
     * <p>It answers with every session for that same reason. Returning only the one switched to
     * would leave the caller's list showing two current years — the new one from this response and
     * the old one it still holds — until something else refetched. An endpoint that rearranges a
     * set answers with the set, as {@code PUT /classes/order} does.
     */
    @PreAuthorize("hasAuthority('academics:session:manage')")
    @PostMapping("/{id}/current")
    public ApiResponse<List<AcademicSessionResponse>> makeCurrent(@PathVariable UUID id) {
        return ApiResponse.success(sessions.makeCurrent(id));
    }
}
