package in.chalkbase.admission.api;

import in.chalkbase.admission.application.AdmissionEnquiryService;
import in.chalkbase.admission.domain.EnquiryQuery;
import in.chalkbase.admission.domain.EnquirySource;
import in.chalkbase.admission.domain.EnquiryStatus;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * This school's admission enquiries: the list, one enquiry's detail, capturing one, assigning its
 * counsellor, and logging a follow-up (FR-016, FR-017).
 *
 * <p>No school id in any path: the session says which school this request works in (ADR-0011).
 *
 * <p>Every payload crossing this boundary is Confidential under ADR-0014: a prospective child's
 * name and a parent's phone number. Nothing here is logged and no error message names a value.
 *
 * <p>The permission strings are literals rather than references to {@code AdmissionPermissions},
 * following every other controller: {@code ControllerAuthorizationTests} is what catches a typo.
 */
@RestController
@RequestMapping("/api/admissions/enquiries")
public class EnquiryController {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final AdmissionEnquiryService enquiries;

    public EnquiryController(AdmissionEnquiryService enquiries) {
        this.enquiries = enquiries;
    }

    /**
     * One page of enquiries, newest first unless the caller sorts otherwise.
     *
     * @param q free text over the child's name, the parent's name and the parent's phone number.
     */
    @PreAuthorize("hasAuthority('admission:enquiry:read')")
    @GetMapping
    public ApiResponse<PageResponse<EnquirySummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EnquiryStatus status,
            @RequestParam(required = false) EnquirySource source,
            @RequestParam(required = false) UUID assignedCounsellorId,
            @RequestParam(required = false) UUID interestedClassId,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        EnquiryQuery query = new EnquiryQuery(q, status, source, assignedCounsellorId, interestedClassId);
        return ApiResponse.success(enquiries.list(query, pageable));
    }

    @PreAuthorize("hasAuthority('admission:enquiry:read')")
    @GetMapping("/{id}")
    public ApiResponse<EnquiryDetailResponse> detail(@PathVariable UUID id) {
        return ApiResponse.success(enquiries.detail(id));
    }

    @PreAuthorize("hasAuthority('admission:enquiry:manage')")
    @PostMapping
    public ResponseEntity<ApiResponse<EnquiryDetailResponse>> create(@Valid @RequestBody CreateEnquiryRequest request) {
        EnquiryDetailResponse created = enquiries.create(request);
        return ResponseEntity.created(URI.create("/api/admissions/enquiries/" + created.id()))
                .body(ApiResponse.success(created));
    }

    /** Assigns or reassigns the counsellor responsible for one enquiry's follow-up. */
    @PreAuthorize("hasAuthority('admission:enquiry:manage')")
    @PostMapping("/{id}/assign")
    public ApiResponse<EnquiryDetailResponse> assign(
            @PathVariable UUID id, @Valid @RequestBody AssignCounsellorRequest request) {
        return ApiResponse.success(enquiries.assign(id, request));
    }

    /** Logs a follow-up against one enquiry, returning the enquiry as it now stands. */
    @PreAuthorize("hasAuthority('admission:enquiry:manage')")
    @PostMapping("/{id}/follow-ups")
    public ApiResponse<EnquiryDetailResponse> logFollowUp(
            @PathVariable UUID id, @Valid @RequestBody LogFollowUpRequest request) {
        return ApiResponse.success(enquiries.logFollowUp(id, request));
    }
}
