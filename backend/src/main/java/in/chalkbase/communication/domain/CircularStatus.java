package in.chalkbase.communication.domain;

/**
 * Where a circular stands. Two states only — there is no {@code SCHEDULED} or {@code ARCHIVED} in
 * this build.
 *
 * <p>{@link #DRAFT} is mutable: title, body and targets may all still change. {@link #PUBLISHED}
 * is a one-way door — {@link Circular#publish} refuses to run twice — because publishing is the
 * act that generates {@code circular_recipient} rows, and a circular that could un-publish would
 * leave those rows meaning nothing.
 */
public enum CircularStatus {
    DRAFT,
    PUBLISHED
}
