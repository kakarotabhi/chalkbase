package in.chalkbase.attendance.api;

import in.chalkbase.attendance.application.AttendanceLeaveService;
import in.chalkbase.attendance.domain.LeaveDecision;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The leave request list, one request's own detail, and the decision on one.
 *
 * <p>Filing lives on {@link AttendanceController} instead, next to {@code mark} — see that
 * controller's own Javadoc on {@code requestLeave} for why. Everything here reads or decides a
 * request that already exists, which is a different permission shape:
 * {@code attendance:leave:read} for the first two, {@code attendance:leave:approve} for the third.
 */
@RestController
@RequestMapping("/api/attendance/leave-requests")
public class AttendanceLeaveController {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final AttendanceLeaveService leave;

    public AttendanceLeaveController(AttendanceLeaveService leave) {
        this.leave = leave;
    }

    /**
     * Every leave request, newest first, or only those in {@code decision} if given (oldest first —
     * the queue shape, same as {@code AttendanceCorrectionController.pending}).
     */
    @PreAuthorize("hasAuthority('attendance:leave:read')")
    @GetMapping
    public ApiResponse<PageResponse<LeaveRequestResponse>> list(
            @RequestParam(required = false) LeaveDecision decision,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "requestedAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.success(leave.list(decision, pageable));
    }

    /** One leave request. */
    @PreAuthorize("hasAuthority('attendance:leave:read')")
    @GetMapping("/{id}")
    public ApiResponse<LeaveRequestResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(leave.get(id));
    }

    /** Approves or rejects one leave request. */
    @PreAuthorize("hasAuthority('attendance:leave:approve')")
    @PostMapping("/{id}/decision")
    public ApiResponse<LeaveRequestResponse> decide(
            @PathVariable UUID id, @Valid @RequestBody DecideLeaveRequest request) {
        return ApiResponse.success(leave.decide(id, request));
    }
}
