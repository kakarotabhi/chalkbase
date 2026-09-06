package in.chalkbase.academics.domain;

/**
 * What this module calls the things it writes to the audit log (ADR-0018).
 *
 * <p>The entity types are the nouns an audit row is filtered by — {@code entity_type} plus
 * {@code entity_id} is an index, and "what happened to this class" is one of the three questions
 * the log exists to answer.
 *
 * <p>{@link #SESSION_MADE_CURRENT} is a verb this module owns rather than a plain
 * {@code ENTITY_UPDATED}, which {@code AuditAction} explicitly allows for exactly this case. Moving
 * a school from one academic year to the next is the single most consequential switch in the
 * academic model — every enrolment, every roll number and every later timetable hangs off it — and
 * "someone updated a field called current" is a worse answer to "when did we move into 2027-28"
 * than a row that says so.
 */
public final class AcademicsAudit {

    public static final String ACADEMIC_SESSION = "ACADEMIC_SESSION";
    public static final String SCHOOL_CLASS = "SCHOOL_CLASS";
    public static final String SECTION = "SECTION";

    /**
     * The session a school is now in.
     *
     * <p>Recorded against the session that <em>became</em> current. The session that stopped being
     * current gets its own ordinary {@code ENTITY_UPDATED} row naming the same field, because the
     * switch changes two rows and a log that recorded only the winner would leave the other one's
     * history silently incomplete — a query on the old session's id would show it becoming current
     * and never stopping.
     */
    public static final String SESSION_MADE_CURRENT = "SESSION_MADE_CURRENT";

    private AcademicsAudit() {}
}
