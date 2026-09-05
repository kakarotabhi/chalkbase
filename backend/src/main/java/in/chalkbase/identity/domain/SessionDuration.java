package in.chalkbase.identity.domain;

import java.time.Duration;

/**
 * How long a session survives without activity.
 *
 * <p>Two values, not a setting: a school office machine is shared by whoever is at the counter, so
 * the default is short enough that walking away is not an unlocked account. "Keep me signed in" is
 * the parent on their own phone, and it is their explicit choice.
 */
public final class SessionDuration {

    /** The default. Matches spring.session.timeout. */
    public static final Duration DEFAULT = Duration.ofHours(8);

    /** Chosen by ticking "keep me signed in". Deliberately days, not weeks. */
    public static final Duration REMEMBERED = Duration.ofDays(7);

    private SessionDuration() {}
}
