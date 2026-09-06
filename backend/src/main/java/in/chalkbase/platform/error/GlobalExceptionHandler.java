package in.chalkbase.platform.error;

import in.chalkbase.platform.api.ApiError;
import in.chalkbase.platform.api.ApiResponse;
import in.chalkbase.platform.audit.AuditService;
import in.chalkbase.platform.web.RequestId;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every exception to the {@link ApiResponse} envelope, so a client only ever parses one shape.
 *
 * <p>Two rules for anything added here:
 *
 * <ul>
 *   <li><b>Never echo an exception message from a parser or the database.</b> Jackson quotes the
 *       source it choked on, which for a login means the password ends up in the response body and
 *       the logs. Database messages leak table and column names. Log them; return a sentence.
 *   <li><b>No domain knowledge.</b> Anything that needs to know what {@code uq_school_code} means
 *       belongs in that module, behind {@link ConstraintMappingProvider}.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ConstraintViolationResolver constraintViolations;
    private final AuditService audit;

    public GlobalExceptionHandler(ConstraintViolationResolver constraintViolations, AuditService audit) {
        this.constraintViolations = constraintViolations;
        this.audit = audit;
    }

    // ── Deliberate application failures ──────────────────────────────────────────────────────

    @ExceptionHandler(ChalkbaseException.class)
    ResponseEntity<ApiResponse<Void>> handleChalkbase(ChalkbaseException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("{}: {}", code.code(), ex.getMessage());
        return respond(code.httpStatus(), ApiError.withDetails(code.code(), ex.getMessage(), ex.getDetails()));
    }

    // ── Authentication and authorization ─────────────────────────────────────────────────────

    /**
     * Bad credentials and unknown user return the same code and sentence on purpose. Distinguishing
     * them turns the login form into a tool for discovering which parents are registered.
     */
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: bad credentials");
        return respond(PlatformErrorCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getClass().getSimpleName());
        return respond(PlatformErrorCode.AUTHENTICATION_REQUIRED);
    }

    /**
     * The 403 raised by a method-level {@code @PreAuthorize}, once the request has already reached
     * a controller.
     *
     * <p>Audited here as well as in {@link SecurityErrorResponder}, and that is not a duplicate: a
     * denial that reaches this advice never reaches the filter chain's handler, because this one
     * writes the response and the exception stops here. The two together cover both ways a 403 is
     * produced, and exactly one of them runs per request (ADR-0018).
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        audit.recordPermissionDenied();
        return respond(PlatformErrorCode.ACCESS_DENIED);
    }

    // ── Validation ───────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.merge(error.getField(), String.valueOf(error.getDefaultMessage()), (a, b) -> a + "; " + b);
        }
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(error ->
                        fieldErrors.putIfAbsent(error.getObjectName(), String.valueOf(error.getDefaultMessage())));

        log.warn("Validation failed on {} field(s)", fieldErrors.size());
        return respond(
                PlatformErrorCode.VALIDATION_FAILED.httpStatus(),
                ApiError.withDetails(
                        PlatformErrorCode.VALIDATION_FAILED.code(),
                        PlatformErrorCode.VALIDATION_FAILED.defaultMessage(),
                        fieldErrors));
    }

    /** {@code @Valid} on a path variable or request parameter rather than on a body. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiResponse<Void>> handleParameterValidation(HandlerMethodValidationException ex) {
        log.warn(
                "Parameter validation failed on {} parameter(s)",
                ex.getParameterValidationResults().size());
        return respond(PlatformErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleBeanValidation(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(violation ->
                        fieldErrors.put(String.valueOf(violation.getPropertyPath()), violation.getMessage()));

        log.warn("Constraint violation on {} field(s)", fieldErrors.size());
        return respond(
                PlatformErrorCode.VALIDATION_FAILED.httpStatus(),
                ApiError.withDetails(
                        PlatformErrorCode.VALIDATION_FAILED.code(),
                        PlatformErrorCode.VALIDATION_FAILED.defaultMessage(),
                        fieldErrors));
    }

    // ── Malformed requests ───────────────────────────────────────────────────────────────────
    //
    // Spring raises a different exception for each way a request can be wrong before it reaches a
    // controller. Left unhandled they all fall through to the catch-all below and come back as
    // 500 GEN_001, which is wrong twice: the client is told the server broke when the request did
    // and cannot act on the answer, and real 500s get buried among client mistakes.

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        return respond(
                HttpStatus.BAD_REQUEST,
                ApiError.of(
                        PlatformErrorCode.VALIDATION_FAILED.code(),
                        "Required parameter '" + ex.getParameterName() + "' is missing"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parameter type mismatch on '{}'", ex.getName());
        String expected = ex.getRequiredType() == null
                ? "the expected type"
                : ex.getRequiredType().getSimpleName();
        return respond(
                HttpStatus.BAD_REQUEST,
                ApiError.of(
                        PlatformErrorCode.VALIDATION_FAILED.code(),
                        "Parameter '" + ex.getName() + "' is not a valid " + expected));
    }

    /**
     * An unknown property in a {@code ?sort=} parameter.
     *
     * <p>Spring Data resolves sort properties against the entity when the query runs, so a typo
     * arrives here as a repository failure rather than as bound-parameter validation — and without
     * this it would reach {@code handleUnexpected} and be answered with a 500. A caller's own typo
     * is a 400: the request was wrong, nothing at our end was.
     *
     * <p>The property name is echoed because the caller supplied it. What is <em>not</em> echoed is
     * {@code ex.getMessage()}, which lists every property the entity has — a free schema dump for
     * anyone who can call a list endpoint.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    ResponseEntity<ApiResponse<Void>> handleUnknownSortProperty(PropertyReferenceException ex) {
        log.warn("Unknown sort property '{}'", ex.getPropertyName());
        return respond(
                HttpStatus.BAD_REQUEST,
                ApiError.of(
                        PlatformErrorCode.VALIDATION_FAILED.code(),
                        "'" + ex.getPropertyName() + "' is not a field this list can be sorted by"));
    }

    /**
     * Body absent, truncated, or not JSON.
     *
     * <p>Deliberately does not include the exception message: Jackson quotes the offending source,
     * so echoing it puts the request body into the response and the logs.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body ({})", ex.getClass().getSimpleName());
        return respond(PlatformErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        log.warn("Missing request part: {}", ex.getRequestPartName());
        return respond(
                HttpStatus.BAD_REQUEST,
                ApiError.of(
                        PlatformErrorCode.VALIDATION_FAILED.code(),
                        "Required file '" + ex.getRequestPartName() + "' is missing"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Upload exceeded the configured maximum");
        return respond(PlatformErrorCode.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMethod());
        return respond(
                PlatformErrorCode.METHOD_NOT_ALLOWED.httpStatus(),
                ApiError.of(
                        PlatformErrorCode.METHOD_NOT_ALLOWED.code(),
                        "HTTP " + ex.getMethod() + " is not supported on this address"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getContentType());
        return respond(PlatformErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNoSuchEndpoint(NoResourceFoundException ex) {
        log.warn("No endpoint for {}", ex.getResourcePath());
        return respond(PlatformErrorCode.NO_SUCH_ENDPOINT);
    }

    // ── Persistence ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return constraintViolations
                .resolve(ex)
                .map(mapping -> {
                    log.warn("Constraint {} violated", mapping.constraintName());
                    return respond(
                            mapping.errorCode().httpStatus(),
                            ApiError.of(mapping.errorCode().code(), mapping.message()));
                })
                .orElseGet(() -> {
                    // An unclaimed constraint: the client gets a generic conflict, and we get the
                    // constraint's NAME and nothing else.
                    //
                    // Logging the exception here would be the natural thing and it leaks: PostgreSQL
                    // puts its DETAIL line in the message — `Key (admission_number)=(2026/0001)
                    // already exists` — so the values that clashed end up in the log, and in this
                    // product those are a child's identifiers (ADR-0014). The constraint name is
                    // the actionable half anyway: it says which rule fired and therefore which
                    // module owes a ConstraintMapping. The trace id in the response ties it back to
                    // the request.
                    log.error(
                            "Unmapped data integrity violation on constraint {} ({}). Add a"
                                    + " ConstraintMapping for it so callers get a usable message.",
                            constraintViolations.constraintName(ex).orElse("<not reported by the driver>"),
                            ex.getClass().getSimpleName());
                    return respond(PlatformErrorCode.CONFLICT);
                });
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Void>> handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        log.warn("Concurrent update rejected: {}", ex.getMessage());
        return respond(PlatformErrorCode.CONCURRENT_UPDATE);
    }

    // ── Everything else ──────────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        // The trace id is on the MDC, so this line and the client's response can be tied together.
        log.error("Unhandled exception [traceId={}]", RequestId.current(), ex);
        return respond(PlatformErrorCode.INTERNAL);
    }

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code) {
        return respond(code.httpStatus(), ApiError.of(code.code(), code.defaultMessage()));
    }

    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, ApiError error) {
        return ResponseEntity.status(status).body(ApiResponse.error(error));
    }
}
