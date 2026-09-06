package in.chalkbase.student.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A placement corrected: moved to another section, given or given a different roll number, or ended.
 *
 * <p><strong>The academic year is not editable</strong>, and that is the same decision as a section
 * not being movable between classes. A student in Class 5 last year and Class 6 this year is two
 * rows; rewriting the year on one of them would erase the history that making promotion a new row
 * exists to keep, and would silently move a roll number into a different year's uniqueness scope.
 *
 * <p>{@code active} is boxed and {@code @NotNull} so that a client which omits it is told, rather
 * than silently ending a child's enrolment — the same reason {@code UpdateSectionRequest} boxes its
 * own. Setting it back to true is refused if the student has since been enrolled elsewhere in the
 * same year.
 */
public record UpdateEnrolmentRequest(
        @NotNull UUID sectionId,
        @Size(max = 20) String rollNumber,
        @NotNull Boolean active) {}
