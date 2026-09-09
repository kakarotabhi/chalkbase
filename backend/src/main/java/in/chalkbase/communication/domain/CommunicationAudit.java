package in.chalkbase.communication.domain;

/**
 * What this module calls the things it writes to the audit log (ADR-0018).
 *
 * <p>{@link #PUBLISHED} is recorded once per circular, against the circular itself, with
 * {@code recordCount} carrying how many recipients were generated — the same shape
 * {@code AttendanceAudit#MARKS_RECORDED} uses for a bulk write, and for the same reason: creating
 * three hundred recipient rows is one act, publishing, not three hundred.
 */
public final class CommunicationAudit {

    public static final String CIRCULAR = "CIRCULAR";
    public static final String CIRCULAR_RECIPIENT = "CIRCULAR_RECIPIENT";

    /** A circular was published: targets locked, recipients generated. Recorded against the circular. */
    public static final String PUBLISHED = "CIRCULAR_PUBLISHED";

    /** A recipient's acknowledgement was recorded on their behalf. Recorded against the recipient row. */
    public static final String ACKNOWLEDGED = "CIRCULAR_RECIPIENT_ACKNOWLEDGED";

    private CommunicationAudit() {}
}
