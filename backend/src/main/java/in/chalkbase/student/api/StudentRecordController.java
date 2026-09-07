package in.chalkbase.student.api;

import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.student.application.StudentRecordService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The four sections of a student's record beyond the core fields, enrolment and guardians
 * (ADR-0020, FR-028): contact, previous school and transfer certificate, medical, and the
 * UDISE+/board compliance identifiers and categories.
 *
 * <p>Every {@code PUT} here is gated on {@code student:student:manage}, the same permission
 * {@code StudentController} uses to correct the core record — entering a UDISE+ category or a blood
 * group is admissions/office work, not a distinct job.
 *
 * <p><strong>The two {@code /restricted} endpoints are different.</strong> They are gated on
 * {@code student:student:reveal_restricted}, a permission {@code student:student:manage} does not
 * imply, and every call is an audited read (ADR-0014) — see {@code StudentRecordService} and
 * {@code StudentAudit#RESTRICTED_DATA_REVEALED}. Nothing else in this module reads a Restricted
 * value in the clear: {@code GET /api/students/{id}} answers with {@link MedicalSummary} and
 * {@link ComplianceSummary}, which carry only whether each Restricted field has been recorded.
 */
@RestController
@RequestMapping("/api/students/{studentId}")
public class StudentRecordController {

    private final StudentRecordService records;

    public StudentRecordController(StudentRecordService records) {
        this.records = records;
    }

    @PreAuthorize("hasAuthority('student:student:manage')")
    @PutMapping("/contact")
    public ApiResponse<ContactDetail> saveContact(
            @PathVariable UUID studentId, @Valid @RequestBody SaveContactRequest request) {
        return ApiResponse.success(records.saveContact(studentId, request));
    }

    @PreAuthorize("hasAuthority('student:student:manage')")
    @PutMapping("/previous-school")
    public ApiResponse<PreviousSchoolDetail> savePreviousSchool(
            @PathVariable UUID studentId, @Valid @RequestBody SavePreviousSchoolRequest request) {
        return ApiResponse.success(records.savePreviousSchool(studentId, request));
    }

    @PreAuthorize("hasAuthority('student:student:manage')")
    @PutMapping("/medical")
    public ApiResponse<MedicalSummary> saveMedical(
            @PathVariable UUID studentId, @Valid @RequestBody SaveMedicalRequest request) {
        return ApiResponse.success(records.saveMedical(studentId, request));
    }

    /**
     * The real values of the six Restricted health fields. Audited on every call
     * ({@code StudentAudit#RESTRICTED_DATA_REVEALED}) — see the class Javadoc.
     */
    @PreAuthorize("hasAuthority('student:student:reveal_restricted')")
    @GetMapping("/medical/restricted")
    public ApiResponse<MedicalDetail> revealMedical(@PathVariable UUID studentId) {
        return ApiResponse.success(records.revealMedical(studentId));
    }

    @PreAuthorize("hasAuthority('student:student:manage')")
    @PutMapping("/compliance")
    public ApiResponse<ComplianceSummary> saveCompliance(
            @PathVariable UUID studentId, @Valid @RequestBody SaveComplianceRequest request) {
        return ApiResponse.success(records.saveCompliance(studentId, request));
    }

    /**
     * The real values of the four Restricted compliance fields. Audited on every call
     * ({@code StudentAudit#RESTRICTED_DATA_REVEALED}) — see the class Javadoc.
     */
    @PreAuthorize("hasAuthority('student:student:reveal_restricted')")
    @GetMapping("/compliance/restricted")
    public ApiResponse<ComplianceDetail> revealCompliance(@PathVariable UUID studentId) {
        return ApiResponse.success(records.revealCompliance(studentId));
    }
}
