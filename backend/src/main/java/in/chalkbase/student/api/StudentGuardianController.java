package in.chalkbase.student.api;

import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.student.application.GuardianService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Which guardians are responsible for one child.
 *
 * <p>Addressed through the student because a link outside a student is not a thing — the same reason
 * a section is created through its class. The person being linked, though, comes from
 * {@link GuardianController}: this endpoint takes an existing guardian's id and never their details,
 * so attaching a father to his second child cannot accidentally create a second father (ADR-0020
 * §5).
 *
 * <p>Gated on {@code student:guardian:manage} rather than {@code student:student:manage}: attaching a
 * parent to a child is guardian work, and a school that lets the admissions desk record a child
 * without letting it rearrange who is responsible for them is expressing a real distinction.
 */
@RestController
@RequestMapping("/api/students/{studentId}/guardians")
public class StudentGuardianController {

    private final GuardianService guardians;

    public StudentGuardianController(GuardianService guardians) {
        this.guardians = guardians;
    }

    @PreAuthorize("hasAuthority('student:guardian:manage')")
    @PostMapping
    public ResponseEntity<ApiResponse<StudentGuardian>> link(
            @PathVariable UUID studentId, @Valid @RequestBody LinkGuardianRequest request) {
        StudentGuardian created = guardians.link(studentId, request);
        return ResponseEntity.created(URI.create("/api/students/" + studentId + "/guardians/" + created.linkId()))
                .body(ApiResponse.success(created));
    }

    /**
     * Changes what this guardian is to this child, or makes them the first contact.
     *
     * <p>Making one primary clears whichever link held it before, in the same transaction —
     * {@code uq_student_guardian_one_primary} is a partial unique index and cannot be deferred. See
     * {@code GuardianService#updateLink}.
     */
    @PreAuthorize("hasAuthority('student:guardian:manage')")
    @PutMapping("/{linkId}")
    public ApiResponse<StudentGuardian> update(
            @PathVariable UUID studentId,
            @PathVariable UUID linkId,
            @Valid @RequestBody UpdateStudentGuardianRequest request) {
        return ApiResponse.success(guardians.updateLink(studentId, linkId, request));
    }

    /**
     * Detaches a guardian from this child. <strong>The one DELETE this module has.</strong>
     *
     * <p>It exists because a guardian wrongly attached to a child must be detachable, and nothing
     * else answers that: a flag would leave the wrong person on the record for anyone reading the
     * table, and editing the link's guardian would be "this child's father is actually someone else"
     * performed as a field edit.
     *
     * <p><strong>What is deleted is the link, not the person.</strong> The guardian record survives,
     * along with every other child they are responsible for — which is precisely what makes this
     * delete safe where a delete on a student or a guardian would not be. Those two do not exist and
     * are not going to (ADR-0020 §6): a student is referenced by fees, attendance and marks and is a
     * record the school must be able to produce years later, and erasure under the DPDP Act is a
     * different operation with its own design that a {@code DELETE} endpoint was never an answer to.
     *
     * <p>204, with no body: there is nothing left to describe, and the child's record is one
     * {@code GET} away.
     */
    @PreAuthorize("hasAuthority('student:guardian:manage')")
    @DeleteMapping("/{linkId}")
    public ResponseEntity<Void> unlink(@PathVariable UUID studentId, @PathVariable UUID linkId) {
        guardians.unlink(studentId, linkId);
        return ResponseEntity.noContent().build();
    }
}
