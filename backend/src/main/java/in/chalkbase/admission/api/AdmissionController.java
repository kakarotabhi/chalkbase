package in.chalkbase.admission.api;

import in.chalkbase.admission.application.AdmissionEnquiryService;
import in.chalkbase.identity.api.UserSummary;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.api.PageResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two admission reads that are not scoped to a single enquiry: who may be assigned one, and
 * which of them are due for a follow-up right now.
 *
 * <p>Separate from {@link EnquiryController} for the same reason {@code AttendanceCorrectionController}
 * is separate from {@code AttendanceController}: neither endpoint here is addressed by an enquiry
 * id, so neither belongs under {@code /enquiries}.
 */
@RestController
@RequestMapping("/api/admissions")
public class AdmissionController {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final AdmissionEnquiryService enquiries;

    public AdmissionController(AdmissionEnquiryService enquiries) {
        this.enquiries = enquiries;
    }

    /** Every account this school could assign an enquiry to — the assignment picker's own read. */
    @PreAuthorize("hasAuthority('admission:enquiry:read')")
    @GetMapping("/counsellors")
    public ApiResponse<List<UserSummary>> counsellors() {
        return ApiResponse.success(enquiries.counsellors());
    }

    /**
     * The due-date follow-up queue (FR-017) — the thing that makes this module more than a mailbox.
     *
     * @param mine true (the default) for the caller's own assigned enquiries, the queue a counsellor
     *     opens Monday morning; false for the whole school's. No explicit sort here, deliberately:
     *     the repository's own {@code order by nextFollowUpDate, createdAt} is the queue's contract,
     *     not a client-chosen one.
     */
    @PreAuthorize("hasAuthority('admission:enquiry:read')")
    @GetMapping("/follow-ups/due")
    public ApiResponse<PageResponse<EnquiryFollowUpQueueItem>> dueFollowUps(
            @RequestParam(defaultValue = "true") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        return ApiResponse.success(enquiries.dueFollowUps(mine, pageable));
    }
}
