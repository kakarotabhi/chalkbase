package in.chalkbase.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.crypto.DecryptionFailedException;
import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

/**
 * A decryption failure must never let {@code GlobalExceptionHandler} put its cause — or anything
 * built from it — into a log line or a response (ADR-0022).
 *
 * <p>This is the same shape of test as {@code DataIntegrityLoggingTests}, for the same reason: the
 * failure mode is a future edit that logs {@code ex} directly "to make it debuggable", which for
 * this exception means printing a JCE exception's stack one frame away from the key. A cause is
 * planted here with a deliberately poisoned message — real {@code javax.crypto} exceptions never say
 * anything this specific, but if a future cause ever did, this proves {@code GlobalExceptionHandler}
 * would still never surface it.
 */
class DecryptionFailureLoggingTests {

    private static final String POISONED_CAUSE_MESSAGE = "key=Zx9SentinelKeyZx9 plaintext=Zx9SentinelCasteZx9";

    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> captured = new ListAppender<>();

    private GlobalExceptionHandler handler;

    @BeforeEach
    void startCapturing() {
        captured.start();
        handlerLogger.addAppender(captured);
        handler = new GlobalExceptionHandler(
                new ConstraintViolationResolver(List.of()), Mockito.mock(AuditService.class));
    }

    @AfterEach
    void stopCapturing() {
        handlerLogger.detachAppender(captured);
        captured.stop();
    }

    @Test
    void logsOnlyTheKeyIdAndNeverTheCauseOrItsMessage() {
        handler.handleDecryptionFailure(decryptionFailureWithAPoisonedCause("v1"));

        String logged =
                captured.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        String throwableProxies = captured.list.stream()
                .map(event -> String.valueOf(event.getThrowableProxy()))
                .reduce("", String::concat);

        assertThat(logged)
                .as("the key id is a version label, safe, and actionable")
                .contains("v1");
        assertThat(logged)
                .as("GlobalExceptionHandler must never serialise a decryption failure's cause")
                .doesNotContain("Zx9SentinelKeyZx9")
                .doesNotContain("Zx9SentinelCasteZx9");
        assertThat(throwableProxies)
                .as("no Throwable — this exception or its cause — is ever handed to the logger")
                .doesNotContain("Zx9SentinelKeyZx9")
                .doesNotContain("Zx9SentinelCasteZx9")
                .doesNotContain(POISONED_CAUSE_MESSAGE);
    }

    @Test
    void theResponseNeverCarriesTheCauseOrAnythingBuiltFromIt() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDecryptionFailure(decryptionFailureWithAPoisonedCause("v1"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        String body = response.getBody().toString();
        assertThat(body)
                .as("the response carries GEN_002's fixed sentence, never the exception's own message")
                .doesNotContain("Zx9SentinelKeyZx9")
                .doesNotContain("Zx9SentinelCasteZx9")
                .contains(PlatformErrorCode.DECRYPTION_FAILED.code());
    }

    /**
     * {@code DecryptionFailedException}'s constructor is package-private to {@code platform.crypto}
     * — deliberately, so nothing outside that package can construct one with an arbitrary message.
     * This test needs one with a specific, poisoned cause to prove the handler ignores it, so it
     * reaches through reflection rather than widening that constructor for a test's convenience.
     */
    private static DecryptionFailedException decryptionFailureWithAPoisonedCause(String keyId) {
        try {
            Constructor<DecryptionFailedException> constructor =
                    DecryptionFailedException.class.getDeclaredConstructor(String.class, String.class, Throwable.class);
            constructor.setAccessible(true);
            return constructor.newInstance(keyId, "poisoned for a test", new RuntimeException(POISONED_CAUSE_MESSAGE));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
