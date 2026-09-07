package in.chalkbase.attendance.api;

import in.chalkbase.attendance.application.AttendanceCorrectionService;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An administrator's queue of attendance correction requests, and the decision on one.
 *
 * <p>Separate from {@link in.chalkbase.attendance.api.AttendanceController}: everything here is
 * gated on {@code attendance:correction:approve}, held by nobody who merely marks a register.
 */
@RestController
@RequestMapping("/api/attendance/correction-requests")
public class AttendanceCorrectionController {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final AttendanceCorrectionService corrections;

    public AttendanceCorrectionController(AttendanceCorrectionService corrections) {
        this.corrections = corrections;
    }

    /** Requests awaiting a decision, oldest first. */
    @PreAuthorize("hasAuthority('attendance:correction:approve')")
    @GetMapping
    public ApiResponse<PageResponse<CorrectionRequestResponse>> pending(
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "requestedAt") Pageable pageable) {
        return ApiResponse.success(corrections.pendingQueue(pageable));
    }

    /** Approves or rejects one request. */
    @PreAuthorize("hasAuthority('attendance:correction:approve')")
    @PostMapping("/{id}/decision")
    public ApiResponse<CorrectionRequestResponse> decide(
            @PathVariable UUID id, @Valid @RequestBody DecideCorrectionRequest request) {
        return ApiResponse.success(corrections.decide(id, request));
    }
}
