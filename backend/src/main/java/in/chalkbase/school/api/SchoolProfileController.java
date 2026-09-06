package in.chalkbase.school.api;

import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.school.application.SchoolProfileService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current school's own profile.
 *
 * <p><strong>No id in the path, on purpose.</strong> {@code /api/school/profile} is singular and
 * takes no parameter because the tenant is the school (ADR-0011): the session says which schema
 * this request works in, and an id in the URL would be a second, weaker answer to a question the
 * session has already answered — the kind that ends with one school editing another's address.
 *
 * <p>Distinct from {@code /api/schools}, which is the platform's register of every campus. That one
 * is a platform-operator screen; this one is a school administrator editing their own school.
 *
 * <p>The permission strings are literals rather than references to {@code SchoolPermissions},
 * following {@code AccessController}: an annotation needs a compile-time constant, and an inlined
 * one survives a rename no better. {@code ControllerAuthorizationTests} is what catches a typo.
 */
@RestController
@RequestMapping("/api/school/profile")
public class SchoolProfileController {

    private final SchoolProfileService profiles;

    public SchoolProfileController(SchoolProfileService profiles) {
        this.profiles = profiles;
    }

    @PreAuthorize("hasAuthority('school:school:read')")
    @GetMapping
    public ApiResponse<SchoolProfileResponse> profile() {
        return ApiResponse.success(profiles.currentProfile());
    }

    @PreAuthorize("hasAuthority('school:school:update')")
    @PutMapping
    public ApiResponse<SchoolProfileResponse> update(@Valid @RequestBody UpdateSchoolProfileRequest request) {
        return ApiResponse.success(profiles.update(request));
    }
}
