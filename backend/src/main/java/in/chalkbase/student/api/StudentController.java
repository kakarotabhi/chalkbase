package in.chalkbase.student.api;

import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.api.PageResponse;
import in.chalkbase.student.application.StudentService;
import in.chalkbase.student.domain.StudentQuery;
import in.chalkbase.student.domain.StudentStatus;
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
 * This school's students, and where each of them sits.
 *
 * <p>No school id in any path: the session says which school this request works in (ADR-0011), and a
 * parameter saying so again would be a second, weaker answer to a question already answered.
 *
 * <p><strong>There is no DELETE here, and there is not going to be one</strong> (ADR-0020 §6). Fees,
 * attendance and marks all reference a student, and a school that removed one would leave those
 * pointing at nothing while still being legally required to produce the record years later. A child
 * who leaves is {@code WITHDRAWN} or {@code TRANSFERRED} through {@code PUT}. Erasure under the DPDP
 * Act is a different operation with its own design, and a {@code DELETE} endpoint would never have
 * been an answer to it.
 *
 * <p>Enrolment lives under a student rather than on a resource of its own, because an enrolment
 * outside a student is not a thing — the same reason a section is addressed through its class.
 * Editing one is addressed by its own id, since it already knows whose it is.
 *
 * <p>Every payload crossing this boundary is Confidential under ADR-0014: names, dates of birth,
 * admission numbers. Nothing here is logged and no error message names a value.
 *
 * <p>The permission strings are literals rather than references to {@code StudentPermissions},
 * following every other controller: an annotation needs a compile-time constant, and an inlined one
 * survives a rename no better. {@code ControllerAuthorizationTests} is what catches a typo.
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    /** 25 rows — offset pagination, as settled in the Phase 0 decisions and applied everywhere else. */
    private static final int DEFAULT_PAGE_SIZE = 25;

    private final StudentService students;

    public StudentController(StudentService students) {
        this.students = students;
    }

    /**
     * One page of students, by name unless the caller sorts otherwise.
     *
     * <p>{@code ?page=0&size=25&sort=fullName,asc}, plus the filters. Sorted by the whole name,
     * because there is no surname to sort by (ADR-0020 §1) — a class list in an Indian school orders
     * on the full name and always has.
     *
     * <p>Paged, unlike the class ladder: a school has one row here per child rather than one per
     * year, and an unpaged list would send eight hundred children's names in one response to draw a
     * screen showing twenty-five.
     *
     * @param q free text over the student's name and admission number. Not over guardian names: a
     *     student list that answered "which children does this person have" would be a different
     *     screen with different consequences.
     * @param sectionId students with a live enrolment in that section <em>in the school's current
     *     academic year</em>. A school that has not declared a current year gets an empty page rather
     *     than last year's class list quietly relabelled as this year's.
     */
    @PreAuthorize("hasAuthority('student:student:read')")
    @GetMapping
    public ApiResponse<PageResponse<StudentSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) UUID sectionId,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "fullName") Pageable pageable) {
        return ApiResponse.success(students.list(new StudentQuery(q, status, sectionId), pageable));
    }

    @PreAuthorize("hasAuthority('student:student:read')")
    @GetMapping("/{id}")
    public ApiResponse<StudentDetail> find(@PathVariable UUID id) {
        return ApiResponse.success(students.find(id));
    }

    @PreAuthorize("hasAuthority('student:student:manage')")
    @PostMapping
    public ResponseEntity<ApiResponse<StudentDetail>> create(@Valid @RequestBody SaveStudentRequest request) {
        StudentDetail created = students.create(request);
        return ResponseEntity.created(URI.create("/api/students/" + created.id()))
                .body(ApiResponse.success(created));
    }

    @PreAuthorize("hasAuthority('student:student:manage')")
    @PutMapping("/{id}")
    public ApiResponse<StudentDetail> update(@PathVariable UUID id, @Valid @RequestBody SaveStudentRequest request) {
        return ApiResponse.success(students.update(id, request));
    }

    /**
     * Places a student in a section for an academic year.
     *
     * <p>Gated on {@code student:student:manage} rather than on a permission of its own: an
     * enrolment is a fact about a student, and a school that lets someone admit a child but not place
     * them in a class has invented a role that cannot finish its own job.
     */
    @PreAuthorize("hasAuthority('student:student:manage')")
    @PostMapping("/{id}/enrolments")
    public ResponseEntity<ApiResponse<Enrolment>> enrol(
            @PathVariable UUID id, @Valid @RequestBody CreateEnrolmentRequest request) {
        Enrolment created = students.enrol(id, request);
        return ResponseEntity.created(URI.create("/api/students/" + id + "/enrolments/" + created.id()))
                .body(ApiResponse.success(created));
    }

    /** Moves a placement, renumbers it, ends it or brings it back. The academic year is not editable. */
    @PreAuthorize("hasAuthority('student:student:manage')")
    @PutMapping("/{id}/enrolments/{enrolmentId}")
    public ApiResponse<Enrolment> updateEnrolment(
            @PathVariable UUID id, @PathVariable UUID enrolmentId, @Valid @RequestBody UpdateEnrolmentRequest request) {
        return ApiResponse.success(students.updateEnrolment(id, enrolmentId, request));
    }
}
