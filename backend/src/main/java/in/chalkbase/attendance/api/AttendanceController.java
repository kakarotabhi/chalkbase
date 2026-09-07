package in.chalkbase.attendance.api;

import in.chalkbase.attendance.application.AttendanceMarkingService;
import in.chalkbase.platform.api.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Daily attendance: marking a section, viewing it, and one student's history.
 *
 * <p>Only the daily grain has a write path in this build (ADR-0030). There is no
 * {@code periodNumber} anywhere on this controller's surface — a period-wise screen is later work,
 * and its endpoints join this file rather than overload these.
 *
 * <p>The permission strings are literals, for the same reason they are on every other controller in
 * the codebase: an annotation needs a compile-time constant, and {@code ControllerAuthorizationTests}
 * is what catches a typo against {@code AttendancePermissions}.
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceMarkingService marking;

    public AttendanceController(AttendanceMarkingService marking) {
        this.marking = marking;
    }

    /**
     * A section's roster for one date, with each student's mark if one exists yet.
     *
     * @param date defaults to today — the marking screen's own common case, opened first thing in
     *     the morning with nothing chosen yet.
     */
    @PreAuthorize("hasAuthority('attendance:mark:read')")
    @GetMapping("/sections/{sectionId}")
    public ApiResponse<SectionAttendanceView> view(
            @PathVariable UUID sectionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(marking.view(sectionId, date != null ? date : LocalDate.now()));
    }

    /** Marks or edits a section's attendance for one date, every entry in one transaction. */
    @PreAuthorize("hasAuthority('attendance:mark:manage')")
    @PostMapping("/sections/{sectionId}")
    public ApiResponse<SectionAttendanceView> mark(
            @PathVariable UUID sectionId, @Valid @RequestBody MarkAttendanceRequest request) {
        return ApiResponse.success(marking.mark(sectionId, request));
    }

    /** One student's daily attendance between two dates, inclusive. */
    @PreAuthorize("hasAuthority('attendance:mark:read')")
    @GetMapping("/students/{studentId}")
    public ApiResponse<List<StudentAttendanceRecord>> studentHistory(
            @PathVariable UUID studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(marking.studentHistory(studentId, from, to));
    }

    /**
     * Files a correction request against a locked mark.
     *
     * <p>Same permission as marking ({@code attendance:mark:manage}): filing a correction is the
     * locked-date shape of the same act, per {@code AttendancePermissions}.
     */
    @PreAuthorize("hasAuthority('attendance:mark:manage')")
    @PostMapping("/marks/{markId}/correction-requests")
    public ApiResponse<CorrectionRequestResponse> requestCorrection(
            @PathVariable UUID markId, @Valid @RequestBody RequestCorrectionRequest request) {
        return ApiResponse.success(marking.requestCorrection(markId, request));
    }

    /** Every correction request ever filed against one mark, newest first. */
    @PreAuthorize("hasAuthority('attendance:mark:read')")
    @GetMapping("/marks/{markId}/correction-requests")
    public ApiResponse<List<CorrectionRequestResponse>> correctionHistory(@PathVariable UUID markId) {
        return ApiResponse.success(marking.correctionHistory(markId));
    }
}
