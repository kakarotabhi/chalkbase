package in.chalkbase.student.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A guardian created, or a guardian's details corrected. One shape for both.
 *
 * <p>No relation and no student here, deliberately. This creates or edits a <em>person</em>
 * (ADR-0017 §4, ADR-0020 §5) — who they are to a particular child is on the link, and attaching them
 * to one is {@code POST /api/students/{id}/guardians}. A create that took a student id would make
 * "add this father to his second child" indistinguishable from "create a second father", which is
 * the duplication this model exists to avoid.
 *
 * <p>Editing here reaches every child this person is linked to, in one write, which is the whole
 * point: correcting a phone number once corrects it for all four siblings.
 *
 * @param phone loosely bounded rather than pattern-matched. Indian numbers arrive with a country
 *     code, a leading zero, spaces or a hyphen, and a regex tight enough to be useful rejects a real
 *     number the school has on paper — at which point the clerk puts it in the name field. The
 *     column is 20 characters and that is the constraint that matters.
 * @param email optional. Most parents give a phone number and no address, and requiring one would
 *     be answered with {@code na@na.com}.
 */
public record SaveGuardianRequest(
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 20) String phone,
        @Email @Size(max = 320) String email,
        @Size(max = 120) String occupation) {}
