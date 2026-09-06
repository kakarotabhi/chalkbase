package in.chalkbase.student.api;

import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.api.PageResponse;
import in.chalkbase.student.application.GuardianService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
 * The directory of guardians, as people rather than as somebody's parent.
 *
 * <p><strong>This endpoint exists so the office can attach an existing guardian to a sibling instead
 * of typing them in again</strong>, which is the entire point of ADR-0020 §5. A father of four is one
 * row here, so correcting his phone number once corrects it for all four children; with a copy per
 * child, the school that fixes one leaves the other three holding a number that no longer answers
 * and nothing that knows they disagree.
 *
 * <p><strong>There is no DELETE.</strong> A guardian wrongly attached to a child is detached — see
 * {@link StudentGuardianController} — and the person survives, because they are usually somebody
 * else's parent too. Removing the person is not a correction anyone needs and would orphan every
 * other link they hold (ADR-0020 §6).
 *
 * <p>Names, phone numbers and email addresses are all Confidential under ADR-0014: none is logged,
 * and none appears in an error message.
 */
@RestController
@RequestMapping("/api/guardians")
public class GuardianController {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final GuardianService guardians;

    public GuardianController(GuardianService guardians) {
        this.guardians = guardians;
    }

    /**
     * One page of guardians, by name unless the caller sorts otherwise, each with how many children
     * they are linked to.
     *
     * <p>The count is what makes the screen work: a search for a common name returns several
     * indistinguishable rows, and "3 students" is what tells the clerk this is already somebody's
     * parent rather than a stranger they are about to duplicate.
     *
     * @param q free text over name, email and phone. The phone half compares <strong>digits to
     *     digits</strong>: the term is stripped to its digits and matched against the digits of the
     *     stored number, so a clerk typing {@code 98765 43210} finds a guardian entered as
     *     {@code +919876543210}. Nothing is normalised on write — the number stays as the school
     *     typed it, because that is what the school reads back and dials.
     */
    @PreAuthorize("hasAuthority('student:guardian:read')")
    @GetMapping
    public ApiResponse<PageResponse<GuardianSummary>> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "fullName") Pageable pageable) {
        return ApiResponse.success(guardians.list(q, pageable));
    }

    /**
     * Which children this guardian is responsible for — the expansion of {@code linkedStudentCount}.
     *
     * <p>The count on the list says the shared record is doing its job; this says which children it
     * reaches, which is what somebody verifying a suspected duplicate actually needs. Two rows both
     * reading "Suresh Kulkarni, linked to 2 students" is not a question a number can answer, and the
     * answer a clerk reaches for instead is a third record.
     *
     * <p><strong>Gated on {@code student:student:read}, not {@code student:guardian:read}, and that
     * is the decision on this endpoint.</strong> What comes back is student data — a child's name,
     * their admission number, the class they sit in — so it is guarded by the permission that guards
     * student data everywhere else. The two permissions are separate precisely so a school can hand
     * somebody the guardian directory without handing them the roll (see {@code StudentPermissions}),
     * and an endpoint hanging off {@code /api/guardians} must not be the hole in that. Someone
     * holding only the guardian read still gets the count on the list; they do not get the names.
     * Where it is arguable the more protective reading wins, and here it is barely arguable.
     *
     * <p>Not paged, unlike every other list in this module. A guardian has a handful of children —
     * four is a large family, not a large page — and the envelope would be ceremony with nothing
     * behind it.
     */
    @PreAuthorize("hasAuthority('student:student:read')")
    @GetMapping("/{id}/students")
    public ApiResponse<List<GuardianStudent>> students(@PathVariable UUID id) {
        return ApiResponse.success(guardians.studentsOf(id));
    }

    @PreAuthorize("hasAuthority('student:guardian:manage')")
    @PostMapping
    public ResponseEntity<ApiResponse<GuardianSummary>> create(@Valid @RequestBody SaveGuardianRequest request) {
        GuardianSummary created = guardians.create(request);
        return ResponseEntity.created(URI.create("/api/guardians/" + created.id()))
                .body(ApiResponse.success(created));
    }

    /** Corrects a person's details for every child they are linked to, in one write. */
    @PreAuthorize("hasAuthority('student:guardian:manage')")
    @PutMapping("/{id}")
    public ApiResponse<GuardianSummary> update(@PathVariable UUID id, @Valid @RequestBody SaveGuardianRequest request) {
        return ApiResponse.success(guardians.update(id, request));
    }
}
