package in.chalkbase.academics.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A class renamed, retired or brought back.
 *
 * <p><strong>{@code sequence} is deliberately absent</strong>, for the reason it is absent from
 * {@code CreateSchoolClassRequest}: moving one class to a position another class holds is a
 * conflict, and the operation a school actually wants — "put these in this order" — is a single
 * transaction over the whole ladder.
 *
 * <p>{@code active} is a boxed {@code Boolean} with {@code @NotNull} rather than a primitive. A
 * primitive would default a missing field to {@code false}, so a client that forgot to send it
 * would silently retire the class rather than be told it sent an incomplete form.
 */
public record UpdateSchoolClassRequest(
        @NotBlank @Size(max = 40) String name, @NotNull Boolean active) {}
