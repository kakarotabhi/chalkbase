package in.chalkbase.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import in.chalkbase.platform.audit.AuditService;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * A constraint violation must never put the values that clashed into the log (ADR-0014).
 *
 * <p>This is a leak with a very natural shape: the obvious way to log an unmapped violation is to
 * pass the exception, and PostgreSQL puts its {@code DETAIL} line inside the message — {@code Key
 * (admission_number)=(2026/0001) already exists}. In this product the things that clash are a
 * child's admission number, a guardian's phone number, a roll number. The constraint's name is the
 * half that is actually actionable; the values are the half that must not be kept.
 *
 * <p>Asserted by capturing the log rather than by reading the code, because the failure mode is a
 * future edit that adds the exception back "to make it debuggable".
 */
class DataIntegrityLoggingTests {

    /** What PostgreSQL actually says, DETAIL and all. The child's identifiers are in here. */
    private static final String DRIVER_MESSAGE =
            "ERROR: duplicate key value violates unique constraint \"uq_student_admission_number\"\n"
                    + "  Detail: Key (admission_number)=(2026/0001) already exists.";

    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> captured = new ListAppender<>();

    private GlobalExceptionHandler handler;

    @BeforeEach
    void startCapturing() {
        captured.start();
        handlerLogger.addAppender(captured);
        // No module claims this constraint, so the unmapped branch is the one that runs.
        handler = new GlobalExceptionHandler(
                new ConstraintViolationResolver(List.of()), Mockito.mock(AuditService.class));
    }

    @AfterEach
    void stopCapturing() {
        handlerLogger.detachAppender(captured);
        captured.stop();
    }

    @Test
    void logsAnUnmappedConstraintByNameAndNeverByTheValuesThatClashed() {
        handler.handleDataIntegrity(violationOf("uq_student_admission_number"));

        String logged =
                captured.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        String withThrowables = logged
                + captured.list.stream()
                        .map(event -> String.valueOf(event.getThrowableProxy()))
                        .reduce("", String::concat);

        assertThat(logged)
                .as("the constraint name is the actionable half and is kept")
                .contains("uq_student_admission_number");
        assertThat(withThrowables)
                .as("the values that clashed are a child's identifiers and must not be logged")
                .doesNotContain("2026/0001")
                .doesNotContain("Detail: Key");
    }

    @Test
    void saysSoPlainlyWhenTheDriverReportsNoConstraintName() {
        handler.handleDataIntegrity(violationOf(null));

        assertThat(captured.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .reduce("", String::concat))
                .contains("<not reported by the driver>")
                .doesNotContain("2026/0001");
    }

    private static DataIntegrityViolationException violationOf(String constraintName) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException(
                        DRIVER_MESSAGE, new java.sql.SQLException(DRIVER_MESSAGE), constraintName));
    }
}
