package in.chalkbase.student.api;

import in.chalkbase.student.domain.GuardianRelation;
import jakarta.validation.constraints.NotNull;

/**
 * What a guardian is to this child, and whether they are the first contact.
 *
 * <p>Neither the student nor the guardian is editable. Changing the person on a link would be
 * "this child's father is actually someone else" performed as a field edit, which is not a
 * correction anyone should be able to make without noticing: it is a link to detach and a link to
 * create, and this module has a {@code DELETE} for exactly that.
 *
 * <p>The person's own details — name, phone, email, occupation — are not here either, because they
 * belong to a record shared with this child's siblings. Editing them is
 * {@code PUT /api/guardians/{id}}, where it is visible that the change reaches every child.
 *
 * <p>Both fields are boxed and {@code @NotNull}, so a client that omits one is told rather than
 * silently demoting a primary contact or resetting a relationship to a default.
 */
public record UpdateStudentGuardianRequest(
        @NotNull GuardianRelation relation, @NotNull Boolean primary) {}
